package com.dto.user;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequestDTO(
        @NotBlank(message = "Google ID token cannot be blank") String idToken) {
}
