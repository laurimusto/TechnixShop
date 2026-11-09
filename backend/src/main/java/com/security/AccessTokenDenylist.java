package com.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class AccessTokenDenylist {

    private final Cache<String, Date> revokedTokens;

    public AccessTokenDenylist(@Value("${jwt.expiration}") long jwtExpirationMs) {
        // flat eviction window is just memory cleanup - it only needs to be at least as
        // long as an access token can ever live. The real "is this still revoked" decision
        // is made in isRevoked() by comparing each entry's own stored expiry against now.
        this.revokedTokens = Caffeine.newBuilder()
                .expireAfterWrite(jwtExpirationMs, TimeUnit.MILLISECONDS)
                .build();
    }

    public void revoke(String jti, Date expiryDate) {
        revokedTokens.put(jti, expiryDate);
    }

    public boolean isRevoked(String jti) {
        Date revokedUntil = revokedTokens.getIfPresent(jti);
        return revokedUntil != null && revokedUntil.after(new Date());
    }
}
