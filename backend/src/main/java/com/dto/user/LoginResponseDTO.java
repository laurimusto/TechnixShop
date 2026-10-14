package com.dto.user;

public record LoginResponseDTO(
        String token,
        String refreshToken,
        String email,
        Long id) {
}
