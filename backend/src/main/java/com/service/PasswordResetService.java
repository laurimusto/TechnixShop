package com.service;

import com.dto.user.ForgotPasswordRequestDTO;
import com.entity.PasswordResetToken;
import com.repository.PasswordResetTokenRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PasswordResetService {
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetService(PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    @Async
    public void requestReset(String email) {

    }
}
