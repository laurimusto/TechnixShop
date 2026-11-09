package com.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
        @NotBlank(message = "E-mail cannot be blank") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Password cannot be blank") @Size(min = 3, message = "Password must be at least 3 characters") String password) {
}
