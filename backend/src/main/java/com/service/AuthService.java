package com.service;

import com.dto.user.LoginResponseDTO;
import com.dto.user.RegisterRequestDTO;

import com.entity.RefreshToken;
import com.entity.User;
import com.exception.*;
import com.repository.RefreshTokenRepository;
import com.security.AccessTokenDenylist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.java.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.repository.UserRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static com.enums.Role.USER;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenDenylist accessTokenDenylist;
    private final TransactionTemplate requiresNewTransactionTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpirationMs;

    @Value("${refresh.expiration}")
    private Long refreshExpirationMs;

    public AuthService(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder, RefreshTokenRepository refreshTokenRepository, AccessTokenDenylist accessTokenDenylist, PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenDenylist = accessTokenDenylist;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
            throw new InvalidEmailException("Email can't be longer than 50 characters");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setRole(USER);
        String hashedPassword = bCryptPasswordEncoder.encode(request.password());
        user.setPassword(hashedPassword);
        User savedUser = userRepository.save(user);
        String token = generateToken(savedUser);
        String refreshToken = createRefreshToken(savedUser, UUID.randomUUID());

        return new LoginResponseDTO(token, refreshToken, savedUser.getEmail(), savedUser.getUserId());
    }

    public LoginResponseDTO login(String email, String password) {
        User user = authenticate(email, password)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        String token = generateToken(user);
        String refreshToken = createRefreshToken(user, UUID.randomUUID());
        return new LoginResponseDTO(token, refreshToken, user.getEmail(), user.getUserId());
    }

    public String generateToken(User user) {


        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.builder()
                .setSubject(String.valueOf(user.getUserId()))
                .claim("role", user.getRole())
                .setId(UUID.randomUUID().toString())
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

    public Optional<User> authenticate(String email, String password) {
        Optional<User> user = userRepository.findByEmail(email);
        if (user.isPresent() && bCryptPasswordEncoder.matches(password, user.get().getPassword())) {
            log.info("Successfully authenticated email: {}", email);
            return user;
        }
        return Optional.empty();
    }

    public String createRefreshToken(User user, UUID familyId) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);


        SecureRandom secureRandom = new SecureRandom(); //generate raw, random token string.
        byte[] randomBytes = new byte[64];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        //hash that raw string deterministically. The same input always produces the same output, which lets you look up refreshtoken later.
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setFamilyId(familyId);
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(expiryDate);
        refreshTokenRepository.save(refreshToken);

        return rawToken;

    }

    @Transactional
    public LoginResponseDTO refresh(String rawRefreshToken) {


        String hashRefreshToken = hashToken(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashRefreshToken).orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found in database!"));

        if(refreshToken.isRevoked()){ //check if its revoked
            // runs in its own committed transaction, independent of this method's - otherwise
            // throwing below would roll back this bulk update too, since both would otherwise
            // share one @Transactional boundary that rolls back on any RuntimeException,
            // silently undoing the family-wide revocation the instant it's needed most.
            UUID familyId = refreshToken.getFamilyId();
            requiresNewTransactionTemplate.executeWithoutResult(status ->
                    refreshTokenRepository.revokeAllByFamilyId(familyId));
            throw new InvalidRefreshTokenException("refreshToken is revoked!");
        }
        if(refreshToken.getExpiryDate().before(new Date())) { //check expiry date
            throw new InvalidRefreshTokenException("refreshToken is expired!");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        String newRefreshToken = createRefreshToken(refreshToken.getUser(), refreshToken.getFamilyId());
        String newAccessToken = generateToken(refreshToken.getUser());

        return new LoginResponseDTO(newAccessToken, newRefreshToken, refreshToken.getUser().getEmail(), refreshToken.getUser().getUserId());
    }


    @Transactional
    public void logout(String jti, Date accessTokenExpiry, String rawRefreshToken) {
        accessTokenDenylist.revoke(jti, accessTokenExpiry);

        if (rawRefreshToken != null) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(refreshToken -> {
                refreshToken.setRevoked(true);
                refreshTokenRepository.save(refreshToken);
            });
        }
    }

    private String hashToken(String rawToken) {
        String tokenHash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            tokenHash = Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new TokenGenerationException("Failed to hash refresh token", e);
        }

        return tokenHash;

    }
}

