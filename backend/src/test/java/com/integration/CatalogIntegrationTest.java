package com.integration;

import com.dto.category.CategoryResponseDTO;
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
    @DisplayName("categories: returns Laptops and Phones as root categories with no children")
    void categories_returnsSeededRootCategories() throws Exception {
        List<CategoryResponseDTO> categories = categories();

        assertThat(categories).extracting(CategoryResponseDTO::name).containsExactlyInAnyOrder("Laptops", "Phones");
        assertThat(categories).allSatisfy(category -> assertThat(category.children()).isEmpty());
    }
}
