package com.dto.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// profile is optional (may be null); @Valid makes the ProfileDTO rules apply when it is sent.
public record RegisterRequestDTO(@NotBlank(message = "E-mail cannot be blank") @Email(message = "Invalid email format") String email,
                                 @NotBlank(message = "Password cannot be blank") @Size(min = 3, message = "Password must be at least 3 characters") String password,
                                 @NotBlank(message = "Repeat password cannot be blank") String repeatPassword,
                                 @NotBlank(message = "CAPTCHA token cannot be blank") String captchaToken,
                                 @Valid ProfileDTO profile) {

    // registering without any profile details
    public RegisterRequestDTO(String email, String password, String repeatPassword, String captchaToken) {
        this(email, password, repeatPassword, captchaToken, null);
    }
}
