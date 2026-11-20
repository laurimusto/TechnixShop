package com.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// second login step: the challenge from step one + the code from the authenticator app
public record TwoFactorLoginRequestDTO(
        @NotBlank(message = "Sign-in expired. Please log in again.")
        String challengeToken,
        @NotBlank(message = "Code cannot be blank")
        @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
        String code) {
}
