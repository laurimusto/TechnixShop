package com.service;

import com.dto.product.ProductResponseDTO;
import com.dto.product.ProductResponseMapper;
import com.dto.product.ProductSearchCriteriaDTO;
import com.entity.Product;
import com.entity.ProductImage;
import com.repository.ProductImageRepository;
import com.repository.ProductRepository;
import com.specification.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    public ProductService(ProductRepository productRepository, ProductImageRepository productImageRepository) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
    }

    public Page<ProductResponseDTO> search(ProductSearchCriteriaDTO criteria, Pageable pageable) {
        // null here means "filter not requested". Neither Specification.where(null) nor .and(null)
        // actually tolerate null in this Spring Data JPA version (confirmed the hard way - .where(null)
        // throws "Specification must not be null" too), so spec always starts as a real, non-null
        // "always true" Specification, and every .and() call below is guarded.
        Specification<Product> nameFilter = criteria.name() != null && !criteria.name().isBlank() ? ProductSpecifications.nameContains(criteria.name()) : null;
        Specification<Product> categoryFilter = criteria.categoryId() != null ? ProductSpecifications.hasCategory(criteria.categoryId()) : null;
        Specification<Product> brandFilter = criteria.brandId() != null ? ProductSpecifications.hasBrand(criteria.brandId()) : null;
        // OR (not AND): a caller may supply only one bound, e.g. "price >= 500" with no upper limit.
        Specification<Product> priceFilter = criteria.maxPrice() != null || criteria.minPrice() != null ? ProductSpecifications.numericBetween("price", criteria.minPrice(), criteria.maxPrice()) : null;
        Specification<Product> weightFilter = criteria.maxWeight() != null || criteria.minWeight() != null ? ProductSpecifications.numericBetween("weightKg", criteria.minWeight(), criteria.maxWeight()) : null;

        Specification<Product> spec = (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        if (nameFilter != null) spec = spec.and(nameFilter);
        if (categoryFilter != null) spec = spec.and(categoryFilter);
        if (brandFilter != null) spec = spec.and(brandFilter);
        if (priceFilter != null) spec = spec.and(priceFilter);
        if (weightFilter != null) spec = spec.and(weightFilter);

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
        Page<Product> products = productRepository.findAll(spec, pageable);

        List<Long> productIds = products.map(Product::getProductId).toList();
        // one batch query for the whole page instead of one query per product
        Map<Long, String> imageUrlByProductId = productImageRepository.findByProduct_ProductIdInOrderByDisplayOrderAsc(productIds).stream()
                .collect(Collectors.toMap(image -> image.getProduct().getProductId(), ProductImage::getUrl, (first, second) -> first));

        return products.map(product -> ProductResponseMapper.from(product, imageUrlByProductId.get(product.getProductId())));
    }
}
