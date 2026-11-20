package com.service;

import com.dto.user.TwoFactorSetupResponseDTO;
import com.entity.User;
import com.exception.ResourceNotFoundException;
import com.exception.TooManyAttemptsException;
import com.exception.TwoFactorException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.repository.UserRepository;
import com.security.TotpSecretCipher;
import com.security.TotpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

// The 2FA lifecycle of one account:
//   setup   -> a new secret is stored (encrypted), but 2FA is still OFF
//   enable  -> the user types a code from the app: proves the QR scan worked, 2FA turns ON
//   (login) -> after the password, a code is required (AuthService.completeTwoFactorLogin)
//   disable -> needs a current code too, so a stolen session alone can't switch 2FA off
@Slf4j
@Service
public class TwoFactorService {

    // A 6-digit code has 1,000,000 possibilities and we accept 3 of them (drift window), so
    // unlimited guessing would succeed fairly quickly. 5 wrong codes -> locked for 5 minutes.
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final TotpSecretCipher cipher;
    private final Clock clock;

    // userId -> wrong codes in a row. In memory, so it resets on restart (same trade-off as the challenge store).
    private final Cache<Long, Integer> failures = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT)
            .build();

    public TwoFactorService(UserRepository userRepository, TotpService totpService, TotpSecretCipher cipher, Clock clock) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Transactional
    public TwoFactorSetupResponseDTO startSetup(long userId) {
        User user = findUser(userId);
        if (user.isTotpEnabled()) {
            throw new TwoFactorException("Two-factor authentication is already enabled.");
        }
        // starting again simply replaces the pending secret (e.g. the user closed the page before scanning)
        byte[] secret = totpService.generateSecret();
        user.setTotpSecret(cipher.encrypt(secret, userId));
        return new TwoFactorSetupResponseDTO(TotpService.toBase32(secret), totpService.otpauthUri(user.getEmail(), secret));
    }

    @Transactional
    public void enable(long userId, String code) {
        User user = findUser(userId);
        if (user.isTotpEnabled()) {
            throw new TwoFactorException("Two-factor authentication is already enabled.");
        }
        if (user.getTotpSecret() == null) {
            throw new TwoFactorException("Start the setup first.");
        }
        checkCode(user, code);
        user.setTotpEnabled(true);
        log.info("2FA enabled for user id: {}", userId);
    }

    @Transactional
    public void disable(long userId, String code) {
        User user = findUser(userId);
        if (!user.isTotpEnabled()) {
            throw new TwoFactorException("Two-factor authentication is not enabled.");
        }
        checkCode(user, code);
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        // totpLastUsedStep stays: it is a point in time, so keeping it can never block a future, newer code
        log.info("2FA disabled for user id: {}", userId);
    }

    // second login step
    @Transactional
    public void verifyLoginCode(long userId, String code) {
        User user = findUser(userId);
        if (!user.isTotpEnabled()) {
            // can only happen if 2FA was switched off between the two login steps
            throw new TwoFactorException("Two-factor authentication is not enabled.");
        }
        checkCode(user, code);
    }

    private void checkCode(User user, String code) {
        long userId = user.getUserId();
        Integer failed = failures.getIfPresent(userId);
        if (failed != null && failed >= MAX_FAILURES) {
            throw new TooManyAttemptsException("Too many wrong codes. Wait a few minutes and try again.");
        }

        byte[] secret = cipher.decrypt(user.getTotpSecret(), userId);
        OptionalLong step = totpService.matchingStep(secret, code, Instant.now(clock));

        // the code must be correct AND newer than the last accepted one (replay guard, see UserRepository)
        if (step.isEmpty() || userRepository.markTotpStepUsed(userId, step.getAsLong()) == 0) {
            failures.asMap().merge(userId, 1, Integer::sum);
            throw new TwoFactorException("Invalid or already used code.");
        }
        failures.invalidate(userId);
    }

    private User findUser(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
