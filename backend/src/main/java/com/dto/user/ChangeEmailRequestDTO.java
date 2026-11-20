package com.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// password = re-authentication: a stolen session alone must not be able to move the account to another inbox
public record ChangeEmailRequestDTO(
        @NotBlank(message = "E-mail cannot be blank")
        @Email(message = "Invalid email format")
        @Size(max = 50, message = "Email can't be longer than 50 characters")
        String newEmail,
        String password) {
}
