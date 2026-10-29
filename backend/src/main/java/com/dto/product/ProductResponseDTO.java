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
        BigDecimal averageRating,
        int ratingCount
) {
}
