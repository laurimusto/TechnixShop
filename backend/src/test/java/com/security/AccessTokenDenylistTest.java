package com.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenDenylistTest {

    private static final long JWT_EXPIRATION_MS = 1_800_000L; // 30 min, same shape as application.properties

    private AccessTokenDenylist denylist;

    @BeforeEach
    void setUp() {
        denylist = new AccessTokenDenylist(JWT_EXPIRATION_MS);
    }

    @Test
    @DisplayName("isRevoked: true right after revoking a jti with a future expiry")
    void isRevoked_trueForJustRevokedToken() {
        Date futureExpiry = new Date(System.currentTimeMillis() + 60_000);

        denylist.revoke("jti-1", futureExpiry);

        assertThat(denylist.isRevoked("jti-1")).isTrue();
    }

    @Test
    @DisplayName("isRevoked: false for a jti that was never revoked")
    void isRevoked_falseForUnknownJti() {
        assertThat(denylist.isRevoked("never-seen-jti")).isFalse();
    }

    @Test
    @DisplayName("isRevoked: false once the stored expiry is in the past, even if Caffeine hasn't evicted the entry yet")
    void isRevoked_falseOnceStoredExpiryHasPassed() {
        Date pastExpiry = new Date(System.currentTimeMillis() - 60_000);

        denylist.revoke("jti-2", pastExpiry);

        assertThat(denylist.isRevoked("jti-2")).isFalse();
    }
}
