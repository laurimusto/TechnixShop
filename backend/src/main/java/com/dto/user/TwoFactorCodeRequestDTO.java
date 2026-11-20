package com.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// enable / disable 2FA: the 6-digit code from the authenticator app proves the user has the phone
public record TwoFactorCodeRequestDTO(
        @NotBlank(message = "Code cannot be blank")
        @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
        String code) {
}
