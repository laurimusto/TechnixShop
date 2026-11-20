package com.integration;

import com.dto.category.CategoryResponseDTO;
import com.dto.product.SearchSuggestionDTO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class CatalogIntegrationTest extends IntegrationTestBase {

    // PageImpl serializes with many extra fields (pageable, sort, first, empty, ...) that
    // aren't needed here - ignoring unknowns keeps this resilient to that shape.
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PageResponse(List<Map<String, Object>> content, long totalElements, int totalPages) {
    }

    private PageResponse search(String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/search?" + query)).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), PageResponse.class);
    }

    private List<CategoryResponseDTO> categories() throws Exception {
        MvcResult result = mockMvc.perform(get("/categories")).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CategoryResponseDTO.class));
    }

    @Test
    @DisplayName("search: returns the real seeded catalog data with working pagination")
    void search_returnsSeededProductsWithPagination() throws Exception {
        PageResponse page = search("page=0&size=10");

        assertThat(page.totalElements()).isGreaterThanOrEqualTo(64); // 32 laptops + 32 phones seeded
        assertThat(page.content()).hasSize(10);
    }

    @Test
    @DisplayName("search: filtering by categoryId only returns products from that category")
    void search_filtersByCategory() throws Exception {
        CategoryResponseDTO laptops = categories().stream()
                .filter(c -> c.name().equals("Laptops")).findFirst().orElseThrow();

        PageResponse page = search("categoryId=" + laptops.categoryId() + "&size=50");

        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allSatisfy(product -> assertThat(product.get("categoryName")).isEqualTo("Laptops"));
    }

    @Test
    @DisplayName("search: filtering by price range only returns products within that range")
    void search_filtersByPriceRange() throws Exception {
        PageResponse page = search("minPrice=500&maxPrice=1000&size=50");

        assertThat(page.content()).isNotEmpty();
        assertThat(page.content()).allSatisfy(product -> {
            BigDecimal price = new BigDecimal(product.get("price").toString());
            assertThat(price).isBetween(new BigDecimal("500"), new BigDecimal("1000"));
        });
    }

    @Test
    @DisplayName("search: % and _ in the name filter are matched literally, not as LIKE wildcards")
    void search_nameLikeWildcardsAreLiteral() throws Exception {
        // unescaped, name=% would be the pattern '%%%' and return the whole catalog
        assertThat(search("name=%25").totalElements()).isZero(); // %25 = URL-encoded %
        assertThat(search("name=__").totalElements()).isZero();
        // a normal search still works
        assertThat(search("name=MacBook").totalElements()).isPositive();
    }

    private List<SearchSuggestionDTO> suggestions(String q) throws Exception {
        MvcResult result = mockMvc.perform(get("/search/suggestions").param("q", q)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SearchSuggestionDTO.class));
    }

    @Test
    @DisplayName("suggestions: case-insensitive name match, no login needed, each name only once")
    void suggestions_matchNameCaseInsensitive_distinct() throws Exception {
        List<SearchSuggestionDTO> result = suggestions("MACBOOK");

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(s -> assertThat(s.name().toLowerCase()).contains("macbook"));
        // the seeder creates several products with the same name; the dropdown shows each once
        assertThat(result).extracting(SearchSuggestionDTO::name).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("suggestions: a name starting with the text ranks above one that only contains it")
    void suggestions_prefixRanksFirst() throws Exception {
        // "on" is inside "Lenovo Legion 5" and at the start of "OnePlus ...".
        // Plain alphabetical order would put Lenovo first; the ranking must put OnePlus first.
        List<SearchSuggestionDTO> result = suggestions("on");

        assertThat(result.getFirst().name()).startsWithIgnoringCase("on");
    }

    @Test
    @DisplayName("suggestions: the brand name matches too")
    void suggestions_matchBrand() throws Exception {
        assertThat(suggestions("apple")).isNotEmpty()
                .allSatisfy(s -> assertThat(s.brandName()).isEqualTo("Apple"));
    }

    @Test
    @DisplayName("suggestions: never more than 6, even when far more products match")
    void suggestions_limitedToSix() throws Exception {
        assertThat(suggestions("le")).hasSizeBetween(1, 6); // Apple, Lenovo, Google, ... - dozens of matches
    }

    @Test
    @DisplayName("suggestions: under 2 characters, missing or blank q -> empty list, not an error")
    void suggestions_tooShort_returnsEmpty() throws Exception {
        assertThat(suggestions("a")).isEmpty();
        assertThat(suggestions("   ")).isEmpty();

        MvcResult noParam = mockMvc.perform(get("/search/suggestions")).andReturn();
        assertThat(noParam.getResponse().getStatus()).isEqualTo(200);
        assertThat(noParam.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    @DisplayName("suggestions: % and _ are matched literally, not as LIKE wildcards")
    void suggestions_likeWildcardsAreLiteral() throws Exception {
        // unescaped, "%%" would be "anything" and return 6 products
        assertThat(suggestions("%%")).isEmpty();
        assertThat(suggestions("__")).isEmpty();
    }

    @Test
    @DisplayName("suggestions: SQL injection attempt is treated as plain text")
    void suggestions_sqlInjection_isPlainText() throws Exception {
        assertThat(suggestions("' OR '1'='1")).isEmpty();
        // the table is still there afterwards
        assertThat(suggestions("'; DROP TABLE products; --")).isEmpty();
        assertThat(suggestions("iphone")).isNotEmpty();
    }

    @Test
    @DisplayName("suggestions: very long input is accepted and simply matches nothing")
    void suggestions_veryLongInput() throws Exception {
        assertThat(suggestions("x".repeat(5000))).isEmpty();
    }

    @Test
    @DisplayName("categories: returns Laptops and Phones as root categories with no children")
    void categories_returnsSeededRootCategories() throws Exception {
        List<CategoryResponseDTO> categories = categories();

        assertThat(categories).extracting(CategoryResponseDTO::name).containsExactlyInAnyOrder("Laptops", "Phones");
        assertThat(categories).allSatisfy(category -> assertThat(category.children()).isEmpty());
    }
}
