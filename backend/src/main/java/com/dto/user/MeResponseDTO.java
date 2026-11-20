package com.dto.user;

// What GET /me and PUT /me return: the profile plus read-only facts the frontend needs.
// hasPassword: false for Google-only accounts (the Security tab will use it).
// readyForOrdering: whether checkout (Project 2) would accept this profile.
// twoFactorEnabled: whether login asks for an authenticator code.
public record MeResponseDTO(String email, ProfileDTO profile, boolean hasPassword, boolean readyForOrdering,
                            boolean twoFactorEnabled) {
}
