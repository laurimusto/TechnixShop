package com.service;

import com.dto.user.RegisterRequestDTO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.repository.UserRepository;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

    public RegisterRequestDTO createUser(RegisterRequestDTO request) {
        if (userRepository.existsByEmailIgnoreCase(registerRequestDTO.email())) {
            throw new EmailAlreadyExistsException("Email " + registerRequestDTO.email() + " already exists.");
        }
        if (!RegisterRequestDTO.password().equals(registerRequestDTO.repeatPassword())) {
            throw new PasswordMismatchException("Passwords don't match.");
        }
        if (RegisterRequestDTO.password().length() < 3) {
            throw new PasswordTooShortException("Password must be at least 3 characters long.");
        }
        if (RegisterRequestDTO.email().length() > 50) {
            throw new PasswordTooShortException("Email can't be longer than 50 characters");
        }


    }
}
