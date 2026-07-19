package com.dto.user;

public record LoginResponseDTO(
        String token,
        String email,
        Long id) {
}
