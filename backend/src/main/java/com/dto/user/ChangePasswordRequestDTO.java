package com.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// currentPassword is asked again even though the user is logged in (re-authentication):
// a stolen session or an unlocked computer must not be enough to take over the account.
public record ChangePasswordRequestDTO(@NotBlank(message = "Current password cannot be blank") String currentPassword,
                                       @NotBlank(message = "Password cannot be blank") @Size(min = 3, message = "Password must be at least 3 characters") String newPassword,
                                       @NotBlank(message = "Repeat password cannot be blank") String repeatPassword) {
}
