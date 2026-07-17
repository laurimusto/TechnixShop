package com.dto.user;

public record LoginRequestDTO(String token,
                              String email,
                              Long id) {
}
