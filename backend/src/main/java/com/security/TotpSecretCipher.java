package com.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

// Encrypts TOTP secrets before they go into the database. Hashing is not an option: to check a code the
// server must compute HMAC(secret, time), so it needs the secret itself back. Encryption is reversible,
// but only with the key - and the key lives in the environment (TOTP_ENCRYPTION_KEY), not in the database.
// A stolen database dump alone therefore reveals no secrets.
//
// AES-GCM = encryption + a built-in integrity check: if anyone changes a single bit of the stored value,
// decryption fails instead of returning garbage.
@Component
public class TotpSecretCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;       // the standard nonce size for GCM
    private static final int TAG_BITS = 128;      // size of the integrity check

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TotpSecretCipher(@Value("${totp.encryption-key}") String configuredKey) {
        if (configuredKey == null || configuredKey.length() < 32) {
            // fail at startup, not on the first 2FA login
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY must be at least 32 characters (e.g. openssl rand -base64 32)");
        }
        // SHA-256 turns any long random string into exactly the 32 bytes AES-256 needs (same idea as JWT_SECRET)
        this.key = new SecretKeySpec(sha256(configuredKey), "AES");
    }

    // userId is bound in as "associated data": a secret encrypted for user 5 fails to decrypt for user 7,
    // so someone with database write access can't copy their own known secret onto another account.
    public String encrypt(byte[] secret, long userId) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv); // a new random IV every time - reusing one with the same key breaks GCM
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(associatedData(userId));
            byte[] encrypted = cipher.doFinal(secret);
            // store IV + ciphertext together; the IV is not secret, it just has to be unique
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt TOTP secret", e);
        }
    }

    public byte[] decrypt(String stored, long userId) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            cipher.updateAAD(associatedData(userId));
            return cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("TOTP secret was tampered with, or belongs to another user/key", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not decrypt TOTP secret", e);
        }
    }

    private static byte[] associatedData(long userId) {
        return ("user:" + userId).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
