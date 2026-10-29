package com.dto.product;

import com.entity.Product;

// Flattens a Product entity into a response shape, deliberately avoiding category.getSpecs() /
// spec.getCategories() - that bidirectional relationship serializes into an infinite loop if
// touched directly, which is also why the raw entity should never be returned from the API.
public class ProductResponseMapper {

    public static ProductResponseDTO from(Product product) {
        return new ProductResponseDTO(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCategory().getName(),
                product.getBrand().getName(),
                product.getWeightKg(),
                product.getAverageRating(),
                product.getRatingCount()
        );
    }
}
