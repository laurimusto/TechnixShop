package com.dto.user;

// Re-authentication before deleting: password accounts send `password`, Google-only accounts
// (no password) send a fresh `googleIdToken` from signing in with Google again.
// Both are optional here because which one is required depends on the account - UserService decides.
public record DeleteAccountRequestDTO(String password, String googleIdToken) {
}
