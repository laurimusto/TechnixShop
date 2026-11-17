package com.service;

import com.entity.PasswordResetToken;
import com.entity.User;
import com.repository.PasswordResetTokenRepository;
import com.repository.UserRepository;
import com.security.TokenHashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;


@Service
public class PasswordResetService {
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;


    @Value("${reset.expiration}")
    private long resetExpirationMs;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository, UserRepository userRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    @Async
    public void requestReset(String email) {

        Optional<User> foundUser = userRepository.findByEmailIgnoreCase(email);
        if (foundUser.isEmpty()) {
            return;
        }
        User user = foundUser.get();
        if (user.getPassword() == null) {
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

    }
}
