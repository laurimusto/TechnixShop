package com.service;

import com.dto.product.ProductSearchCriteriaDTO;
import com.entity.Product;
import com.repository.ProductRepository;
import com.specification.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> search(ProductSearchCriteriaDTO criteria, Pageable pageable) {
        Specification<Product> categoryFilter = criteria.categoryId() != null ? ProductSpecifications.hasCategory(criteria.categoryId()) : null;
        Specification<Product> brandFilter = criteria.brandId() != null ? ProductSpecifications.hasBrand(criteria.brandId()) : null;

        Specification.where(categoryFilter).and(brandFilter)

    }
}
