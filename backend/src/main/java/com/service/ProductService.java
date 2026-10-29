package com.service;

import com.dto.product.ProductSearchCriteriaDTO;
import com.entity.Product;
import com.repository.ProductRepository;
import com.specification.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> search(ProductSearchCriteriaDTO criteria, Pageable pageable) {
        // null here means "filter not requested" - Specification.and(null) is a no-op, so absent filters don't restrict the query.
        Specification<Product> categoryFilter = criteria.categoryId() != null ? ProductSpecifications.hasCategory(criteria.categoryId()) : null;
        Specification<Product> brandFilter = criteria.brandId() != null ? ProductSpecifications.hasBrand(criteria.brandId()) : null;
        // OR (not AND): a caller may supply only one bound, e.g. "price >= 500" with no upper limit.
        Specification<Product> priceFilter = criteria.maxPrice() != null || criteria.minPrice() != null ? ProductSpecifications.numericBetween("price", criteria.minPrice(), criteria.maxPrice()) : null;


        Specification<Product> spec = Specification
                .where(categoryFilter)
                .and(brandFilter)
                .and(priceFilter);

        // Text and number specs are reassigned in loop because the number of entries is unknown ahead of time.
        if (criteria.textSpecFilters() != null) {
            for (Map.Entry<String, List<String>> entry : criteria.textSpecFilters().entrySet()) {
                spec = spec.and(ProductSpecifications.hasSpecTextIn(entry.getKey(), entry.getValue()));

            }
        }

        if (criteria.numberSpecFilters() != null) {
            for (Map.Entry<String, List<BigDecimal>> entry : criteria.numberSpecFilters().entrySet()) {
                spec = spec.and(ProductSpecifications.hasSpecNumberIn(entry.getKey(), entry.getValue()));

            }
        }
        return productRepository.findAll(spec, pageable);


    }
}
