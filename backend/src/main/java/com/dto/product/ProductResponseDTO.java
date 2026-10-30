package com.dto.product;

import java.math.BigDecimal;

public record ProductResponseDTO(
        Long productId,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        String categoryName,
        String brandName,
        String imageUrl,
        BigDecimal weightKg,
        BigDecimal weightLb,
        BigDecimal lengthCm,
        BigDecimal lengthIn,
        BigDecimal widthCm,
        BigDecimal widthIn,
        BigDecimal heightCm,
        BigDecimal heightIn,
        BigDecimal averageRating,
        int ratingCount
) {
}
