package com.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequestDTO(@NotBlank(message = "token cannot be blank")  String token,
                                      @NotBlank(message = "Password cannot be blank") @Size(min = 3, message = "Password must be at least 3 characters") String newPassword,
                                      @NotBlank(message = "Repeat password cannot be blank") String repeatPassword) {
}
