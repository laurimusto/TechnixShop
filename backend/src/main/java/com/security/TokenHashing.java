package com.security;

import com.exception.TokenGenerationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenHashing {
    public static String generateRawToken() {
        SecureRandom secureRandom = new SecureRandom(); //generate raw, random token string.
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

    }

    public static String sha256(String rawToken) {
        String tokenHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            tokenHash = Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new TokenGenerationException("Failed to hash token", e);
        }

        return tokenHash;

    }
}
