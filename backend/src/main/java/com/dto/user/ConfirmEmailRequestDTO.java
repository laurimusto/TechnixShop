package com.dto.user;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailRequestDTO(@NotBlank(message = "This confirmation link is invalid or has expired.") String token) {
}
