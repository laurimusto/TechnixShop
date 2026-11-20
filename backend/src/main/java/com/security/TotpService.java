package com.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

// TOTP (RFC 6238) - the algorithm Google Authenticator, Authy etc. use. No library: it is small enough
// to read in one sitting, and every step below is what the authenticator app on the phone does too.
//
//   time step = seconds since 1970 / 30          (both sides compute the same number without talking)
//   hash      = HMAC-SHA1(secret, time step)
//   code      = 6 digits picked out of the hash  ("dynamic truncation", RFC 4226 section 5.3)
@Component
public class TotpService {

    public static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int SECRET_BYTES = 20; // 160 bits, the size RFC 4226 recommends for HMAC-SHA1
    // accept the previous and next step as well: phone clocks drift a little and typing takes time
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final String ISSUER = "Technix";

    private final SecureRandom random = new SecureRandom();

    public byte[] generateSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        return secret;
    }

    public static long timeStep(Instant time) {
        return time.getEpochSecond() / PERIOD_SECONDS;
    }

    public String generateCode(byte[] secret, long timeStep) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(timeStep).array());

            // dynamic truncation: the low 4 bits of the last byte choose where to read 4 bytes from
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)      // & 0x7F drops the sign bit
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int code = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", code); // keep leading zeros: "007081"
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    // Returns the time step the code belongs to (the caller stores it to block replays), or empty if it matches none.
    public OptionalLong matchingStep(byte[] secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = timeStep(now);
        for (long step = current - ALLOWED_DRIFT_STEPS; step <= current + ALLOWED_DRIFT_STEPS; step++) {
            // constant-time comparison: equals() stops at the first different digit, which leaks timing
            if (MessageDigest.isEqual(generateCode(secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    // The link inside the QR code. Authenticator apps read the secret (Base32) and show "Technix (email)".
    public String otpauthUri(String email, byte[] secret) {
        String label = urlEncode(ISSUER + ":" + email);
        return "otpauth://totp/" + label + "?secret=" + toBase32(secret) + "&issuer=" + urlEncode(ISSUER)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // --- Base32 (RFC 4648): the format authenticator apps expect for the secret ---
    // Only A-Z and 2-7, so people can type the key by hand if the QR code doesn't scan (no 0/O or 1/I mix-ups).
    // Each character carries 5 bits, so bytes go in and come out 5 bits at a time.

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static String toBase32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString(); // no "=" padding: authenticator apps don't need it
    }

    // Tolerates what people type: lower case, spaces and "=" padding.
    public static byte[] fromBase32(String text) {
        String clean = text.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not a Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
