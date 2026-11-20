package com.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpSecretCipherTest {

    private static final String KEY = "unit-test-only-totp-encryption-key-123";
    private static final byte[] SECRET = "12345678901234567890".getBytes();

    private final TotpSecretCipher cipher = new TotpSecretCipher(KEY);

    @Test
    @DisplayName("decrypting gives back the original secret")
    void roundTrip() {
        String stored = cipher.encrypt(SECRET, 5L);
        assertThat(cipher.decrypt(stored, 5L)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("the same secret encrypts differently each time (random IV), and never as plain text")
    void randomIv() {
        String a = cipher.encrypt(SECRET, 5L);
        String b = cipher.encrypt(SECRET, 5L);
        assertThat(a).isNotEqualTo(b);
        assertThat(new String(Base64.getDecoder().decode(a))).doesNotContain("1234567890");
    }

    @Test
    @DisplayName("a secret encrypted for one user can't be decrypted as another user's")
    void boundToUser() {
        String stored = cipher.encrypt(SECRET, 5L);
        assertThatThrownBy(() -> cipher.decrypt(stored, 7L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("changing a single bit of the stored value is detected")
    void tamperDetected() {
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt(SECRET, 5L));
        bytes[bytes.length - 1] ^= 1;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> cipher.decrypt(tampered, 5L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a different key can't decrypt it")
    void otherKeyFails() {
        String stored = cipher.encrypt(SECRET, 5L);
        TotpSecretCipher other = new TotpSecretCipher("another-unit-test-totp-encryption-key-456");
        assertThatThrownBy(() -> other.decrypt(stored, 5L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a missing or too short key stops the application at startup")
    void shortKeyRejected() {
        assertThatThrownBy(() -> new TotpSecretCipher("too-short")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TotpSecretCipher(null)).isInstanceOf(IllegalStateException.class);
    }
}
