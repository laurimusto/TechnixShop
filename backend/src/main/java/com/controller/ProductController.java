package com.controller;

import com.dto.product.ProductResponseDTO;
import com.dto.product.ProductSearchCriteriaDTO;
import com.dto.product.SearchSuggestionDTO;
import com.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // Search box dropdown: up to 6 products whose name or brand contains q. Short q -> empty list (200, not 400):
    // the box calls this while the user is still typing, so "too short" is a normal state, not an error.
    @GetMapping("/search/suggestions")
    public List<SearchSuggestionDTO> suggestions(@RequestParam(required = false) String q) {
        return productService.suggest(q);
    }

    @GetMapping("/search")
    public Page<ProductResponseDTO> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minWeight,
            @RequestParam(required = false) BigDecimal maxWeight,
            @RequestParam(required = false) List<String> cpu,
            @RequestParam(required = false) List<String> gpu,
            @RequestParam(name = "ram_gb", required = false) List<BigDecimal> ramGb,
            @RequestParam(name = "display_size_in", required = false) List<BigDecimal> displaySizeIn,
            Pageable pageable) {
        Map<String, List<String>> textSpecFilters = new HashMap<>();
        if (cpu != null) textSpecFilters.put("cpu", cpu);
        if (gpu != null) textSpecFilters.put("gpu", gpu);

        Map<String, List<BigDecimal>> numberSpecFilters = new HashMap<>();
        if (ramGb != null) numberSpecFilters.put("ram_gb", ramGb);
        if (displaySizeIn != null) numberSpecFilters.put("display_size_in", displaySizeIn);

        ProductSearchCriteriaDTO criteria = new ProductSearchCriteriaDTO(name, categoryId, brandId, minPrice, maxPrice, minWeight, maxWeight, textSpecFilters, numberSpecFilters);
        return productService.search(criteria, pageable);
    }
}
