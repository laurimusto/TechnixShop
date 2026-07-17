package com.dto.error;

public record ErrorResponseDTO(
        int status,
        String error,
        String message) {
}
