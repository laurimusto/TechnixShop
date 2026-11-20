package com.dto.user;

// Internal result of a sign-in (service -> controller). Exactly one of two shapes:
//   - signed in:        token + refreshToken + email + id, twoFactorChallenge = null
//   - 2FA code needed:  only twoFactorChallenge, everything else null
public record LoginResponseDTO(
        String token,
        String refreshToken,
        String email,
        Long id,
        String twoFactorChallenge) {

    public LoginResponseDTO(String token, String refreshToken, String email, Long id) {
        this(token, refreshToken, email, id, null);
    }

    public static LoginResponseDTO twoFactorRequired(String challenge) {
        return new LoginResponseDTO(null, null, null, null, challenge);
    }

    public boolean needsTwoFactor() {
        return twoFactorChallenge != null;
    }
}
