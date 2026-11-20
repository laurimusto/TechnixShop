package com.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

// Remembers "this person typed the right password, now waiting for their 2FA code" between the two
// login requests. The client gets a random challenge token instead of a JWT.
//
// Why not a JWT for this? JwtAuthenticationFilter accepts any JWT signed with our key, so a "half logged in"
// JWT would also work as a full access token unless every check knew about it. A random value that only
// this store understands can never be mistaken for an access token.
//
// Kept in memory (like AccessTokenDenylist): fine for one backend instance. With several instances behind
// a load balancer this would move to a shared store such as Redis.
@Component
public class TwoFactorChallengeStore {

    // 5 minutes to open the authenticator app and type the code
    private static final Duration LIFETIME = Duration.ofMinutes(5);

    private final Cache<String, Long> challenges = Caffeine.newBuilder()
            .expireAfterWrite(LIFETIME)
            .build();

    public String start(long userId) {
        String challenge = TokenHashing.generateRawToken();
        challenges.put(challenge, userId);
        return challenge;
    }

    public Optional<Long> userIdFor(String challenge) {
        return Optional.ofNullable(challenges.getIfPresent(challenge));
    }

    // single use: once the code was accepted, this challenge can't log anyone in again
    public void complete(String challenge) {
        challenges.invalidate(challenge);
    }
}
