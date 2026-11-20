package com.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    // the secret RFC 6238 uses for its SHA1 test vectors
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private final TotpService totpService = new TotpService();

    // Official test vectors (RFC 6238, Appendix B). The RFC lists 8-digit codes; a 6-digit code is the
    // same number mod 10^6, i.e. its last 6 digits. If these pass, real authenticator apps will agree with us.
    @ParameterizedTest(name = "T={0}s -> {1}")
    @CsvSource({
            "59,          287082",
            "1111111109,  081804",
            "1111111111,  050471",
            "1234567890,  005924",
            "2000000000,  279037",
            "20000000000, 353130"
    })
    @DisplayName("matches the RFC 6238 test vectors")
    void rfcTestVectors(long epochSeconds, String expected) {
        long step = TotpService.timeStep(Instant.ofEpochSecond(epochSeconds));
        assertThat(totpService.generateCode(RFC_SECRET, step)).isEqualTo(expected);
    }

    @Test
    @DisplayName("accepts the current, previous and next 30-second step, and returns which one matched")
    void acceptsDriftWindow() {
        Instant now = Instant.ofEpochSecond(1_000_000_015L);
        long current = TotpService.timeStep(now);

        for (long step = current - 1; step <= current + 1; step++) {
            String code = totpService.generateCode(RFC_SECRET, step);
            assertThat(totpService.matchingStep(RFC_SECRET, code, now)).hasValue(step);
        }
    }

    @Test
    @DisplayName("rejects a code two steps away (older than ~1 minute)")
    void rejectsOutsideWindow() {
        Instant now = Instant.ofEpochSecond(1_000_000_015L);
        String old = totpService.generateCode(RFC_SECRET, TotpService.timeStep(now) - 2);
        String current = totpService.generateCode(RFC_SECRET, TotpService.timeStep(now));
        // guard against the (tiny) chance both steps produce the same digits
        if (!old.equals(current)) {
            assertThat(totpService.matchingStep(RFC_SECRET, old, now)).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "12345", "1234567", "12a456", "' 12345'"}, nullValues = "NULL")
    @DisplayName("malformed codes are rejected without being checked")
    void rejectsMalformed(String code) {
        assertThat(totpService.matchingStep(RFC_SECRET, code, Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("a different secret produces a code that doesn't verify")
    void otherSecretRejected() {
        Instant now = Instant.ofEpochSecond(1_000_000_015L);
        byte[] other = totpService.generateSecret();
        String code = totpService.generateCode(other, TotpService.timeStep(now));
        assertThat(totpService.matchingStep(RFC_SECRET, code, now)).isEmpty();
    }

    @Test
    @DisplayName("new secrets are 20 random bytes")
    void generatesRandomSecrets() {
        byte[] a = totpService.generateSecret();
        byte[] b = totpService.generateSecret();
        assertThat(a).hasSize(20);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("otpauth URI has the label, Base32 secret and parameters authenticator apps expect")
    void otpauthUri() {
        String uri = totpService.otpauthUri("anna@example.com", RFC_SECRET);
        assertThat(uri).isEqualTo("otpauth://totp/Technix%3Aanna%40example.com"
                + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Technix&algorithm=SHA1&digits=6&period=30");
    }

    // RFC 4648 section 10 test vectors (without "=" padding)
    @ParameterizedTest(name = "\"{0}\" <-> {1}")
    @CsvSource({"f, MY", "fo, MZXQ", "foo, MZXW6", "foob, MZXW6YQ", "fooba, MZXW6YTB", "foobar, MZXW6YTBOI"})
    @DisplayName("Base32 matches RFC 4648 and round-trips")
    void base32(String plain, String encoded) {
        byte[] bytes = plain.getBytes(StandardCharsets.US_ASCII);
        assertThat(TotpService.toBase32(bytes)).isEqualTo(encoded);
        assertThat(TotpService.fromBase32(encoded)).isEqualTo(bytes);
        // what people type: lower case, spaces, padding
        assertThat(TotpService.fromBase32(encoded.toLowerCase() + " ==")).isEqualTo(bytes);
    }
}
