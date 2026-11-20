package com.dto.user;

// What /login and /googlelogin return instead of a token when the account has 2FA on.
// No access token, no refresh cookie: the password alone is not enough to get a session.
public record TwoFactorChallengeDTO(boolean twoFactorRequired, String challengeToken) {
}
