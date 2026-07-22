package com.service;

import com.dto.user.LoginResponseDTO;
import com.dto.user.RegisterRequestDTO;
import com.entity.User;
import com.exception.EmailAlreadyExistsException;
import com.exception.PasswordMismatchException;
import com.exception.PasswordTooShortException;
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

    public LoginResponseDTO createUser(RegisterRequestDTO request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException("Email " + request.email() + " already exists.");
        }
        if (!request.password().equals(request.repeatPassword())) {
            throw new PasswordMismatchException("Passwords don't match.");
        }
        if (request.password().length() < 3) {
            throw new PasswordTooShortException("Password must be at least 3 characters long.");
        }
        if (request.email().length() > 50) {
            throw new PasswordTooShortException("Email can't be longer than 50 characters");
        }
        User user = new User();
        user.setEmail(request.email());
        String hashedPassword = bCryptPasswordEncoder.encode(request.password());
        user.setPassword(hashedPassword);
        User savedUser = userRepository.save(user);
        String token = generateToken(savedUser);

        return new LoginResponseDTO(token, savedUser.getEmail(), savedUser.getId());




    }

    public String generateToken(User user) {
return "asd";
    }

}

