package com.service;

import com.dto.user.ResetPasswordRequestDTO;
import com.entity.PasswordResetToken;
import com.entity.User;
import com.exception.InvalidResetTokenException;
import com.exception.PasswordMismatchException;
import com.repository.PasswordResetTokenRepository;
import com.repository.RefreshTokenRepository;
import com.repository.UserRepository;
import com.security.TokenHashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;


@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    // one message for every bad-token case, so the reply never reveals which check failed
    private static final String INVALID_TOKEN_MESSAGE = "This reset link is invalid or has expired.";

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${reset.expiration}")
    private long resetExpirationMs;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository, UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository, EmailService emailService,
                                BCryptPasswordEncoder passwordEncoder) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    @Async
    public void requestReset(String email) {

        Optional<User> foundUser = userRepository.findByEmailIgnoreCase(email);
        if (foundUser.isEmpty()) {
            return;
        }
        User user = foundUser.get();
        try {
            // Google-only account: there is no password to reset, so point the user to Google login instead
            if (user.getPassword() == null) {
                emailService.sendGoogleAccountEmail(user.getEmail());
                return;
            }

            passwordResetTokenRepository.deleteByUser(user);

            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + resetExpirationMs);

            String rawToken = TokenHashing.generateRawToken();
            String tokenHash = TokenHashing.sha256(rawToken);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setTokenHash(tokenHash);
            resetToken.setExpiryDate(expiryDate);

            resetToken.setUser(user);
            passwordResetTokenRepository.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
        } catch (MailException e) {
            // Unlike change-email, the caller can't be told:
            //  - this runs @Async, the 202 has already been sent
            //  - an error that only accounts which exist can trigger would reveal which emails are registered
            // So: a clear ERROR log for whoever runs the server (user id, not the email - keep personal data
            // out of logs), and undo the token so the previous link, if any, stays the valid one.
            log.error("Password reset email could not be sent (user id {})", user.getUserId(), e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    // Not @Async: the user is waiting on the page for success or an error.
    // @Transactional: the password change, session revocation and token deletion all commit together or not at all.
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {
        // cheapest check first, and a typo doesn't burn the token
        if (!request.newPassword().equals(request.repeatPassword())) {
            throw new PasswordMismatchException("Passwords do not match.");
        }

        String tokenHash = TokenHashing.sha256(request.token());
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidResetTokenException(INVALID_TOKEN_MESSAGE));

        if (token.getExpiryDate().before(new Date()) || token.isUsed()) {
            throw new InvalidResetTokenException(INVALID_TOKEN_MESSAGE);
        }

        // the token says who the user is - nobody is logged in during a reset
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        // JWT iat has whole-second precision; truncating avoids rejecting a token issued in this same second
        user.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));

        refreshTokenRepository.revokeAllByUser(user);
        passwordResetTokenRepository.delete(token);
    }
}
