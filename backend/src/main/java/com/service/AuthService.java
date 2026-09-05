package com.service;

import com.dto.user.LoginRequestDTO;
import com.dto.user.LoginResponseDTO;
import com.dto.user.RegisterRequestDTO;
import com.entity.User;
import com.exception.EmailAlreadyExistsException;
import com.exception.InvalidCredentialsException;
import com.exception.PasswordMismatchException;
import com.exception.PasswordTooShortException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.repository.UserRepository;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

import static com.enums.Role.USER;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpirationMs;

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
        user.setRole(USER);
        String hashedPassword = bCryptPasswordEncoder.encode(request.password());
        user.setPassword(hashedPassword);
        User savedUser = userRepository.save(user);
        String token = generateToken(savedUser);

        return new LoginResponseDTO(token, savedUser.getEmail(), savedUser.getId());
    }

    public LoginResponseDTO login(String email, String password) {
        if (!authenticate(email, password)) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        User user = userRepository.findByEmail(email).orElseThrow(() -> new InvalidCredentialsException("User not found"));

        String token = generateToken(user);
        return new LoginResponseDTO(token, user.getEmail(), user.getId());
    }

    public String generateToken(User user) {


        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject(String.valueOf(user.getId()))
                .claim("role", user.getRole())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        Jws<Claims> jws = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);

        return jws.getBody();
    }

    public boolean authenticate(String email, String password) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent()) {
            if (bCryptPasswordEncoder.matches(password, user.get().getPassword())) {
                System.out.println("Successfully logged in with email: " + email);
                return true;
            } else {
                throw new InvalidCredentialsException("Invalid password");
            }
        } else {
            throw new InvalidCredentialsException("Invalid password");
        }
    }

}

