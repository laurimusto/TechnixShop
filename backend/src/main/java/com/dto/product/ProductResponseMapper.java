package com.dto.product;

import com.entity.Product;
import com.utils.UnitConverter;

// Flattens a Product entity into a response shape, deliberately avoiding category.getSpecs() /
// spec.getCategories() - that bidirectional relationship serializes into an infinite loop if
// touched directly, which is also why the raw entity should never be returned from the API.
public class ProductResponseMapper {

    public static ProductResponseDTO from(Product product, String imageUrl) {
        return new ProductResponseDTO(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCategory().getName(),
                product.getBrand().getName(),
                imageUrl,
                product.getWeightKg(),
                UnitConverter.kgToLbs(product.getWeightKg()),
                product.getLengthCm(),
                UnitConverter.cmToInch(product.getLengthCm()),
                product.getWidthCm(),
                UnitConverter.cmToInch(product.getWidthCm()),
                product.getHeightCm(),
                UnitConverter.cmToInch(product.getHeightCm()),
                product.getAverageRating(),
                product.getRatingCount()
        );
    }
}
