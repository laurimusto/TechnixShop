package com.dto.user;

// Returned once, during setup. The frontend turns otpauthUri into a QR code; secret is the same key
// for typing in by hand. After this response the server never shows the secret again.
public record TwoFactorSetupResponseDTO(String secret, String otpauthUri) {
}
