package com.controller;

import com.dto.product.ProductSearchCriteriaDTO;
import com.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }
    @GetMapping("/search")
    public ProductSearchCriteriaDTO search(@RequestParam ProductSearchCriteriaDTO criteria) {
        productService.search(criteria);

    }
}
