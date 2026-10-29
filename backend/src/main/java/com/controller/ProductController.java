package com.controller;

import com.dto.product.ProductSearchCriteriaDTO;
import com.entity.Product;
import com.service.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }


    @GetMapping("/search")
    public Page<Product> search(@RequestParam Map<String, String> allParams, Pageable pageable) {
        return productService.search(criteria, pageable);

    }
}
