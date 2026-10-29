package com.controller;

import com.dto.product.ProductResponseDTO;
import com.dto.product.ProductResponseMapper;
import com.dto.product.ProductSearchCriteriaDTO;
import com.entity.Product;
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

    @GetMapping("/search")
    public Page<ProductResponseDTO> search(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
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

        ProductSearchCriteriaDTO criteria = new ProductSearchCriteriaDTO(categoryId, brandId, minPrice, maxPrice, textSpecFilters, numberSpecFilters);
        Page<Product> products = productService.search(criteria, pageable);
        return products.map(ProductResponseMapper::from);
    }
}
