package com.dto.category;

import java.util.List;

public record CategoryResponseDTO(
        Long categoryId,
        String name,
        List<CategoryResponseDTO> children
) {
}
