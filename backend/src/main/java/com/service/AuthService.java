package com.service;

import com.dto.user.LoginResponseDTO;
import com.dto.user.RegisterRequestDTO;
import com.entity.RefreshToken;
import com.entity.User;
import com.exception.*;
import com.repository.RefreshTokenRepository;
import com.security.AccessTokenDenylist;
import com.security.GoogleTokenVerifier.GoogleUser;
import com.security.TokenHashing;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.repository.UserRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.enums.Role.USER;

@Slf4j //generates logger
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenDenylist accessTokenDenylist;
    private final TransactionTemplate requiresNewTransactionTemplate;
    private final String dummyPasswordHash;


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
        // hash of a random throwaway value, made with the real encoder so it costs exactly as
        // much to check as a real password hash (see authenticate)
        this.dummyPasswordHash = bCryptPasswordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponseDTO createUser(RegisterRequestDTO request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException("An account with this email already exists.");
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

    @Transactional
    public LoginResponseDTO googleLogin(GoogleUser googleUser) {
        if (!googleUser.emailVerified()) {
            throw new InvalidCredentialsException("Google account email is not verified");
        }

        User user = userRepository.findByGoogleSub(googleUser.sub())
                .orElseGet(() -> createGoogleUser(googleUser));

        String token = generateToken(user);
        String refreshToken = createRefreshToken(user, UUID.randomUUID());
        return new LoginResponseDTO(token, refreshToken, user.getEmail(), user.getUserId());
    }

    private User createGoogleUser(GoogleUser googleUser) {
        // never auto-link: our /register doesn't prove email ownership, so an existing
        // password account with this email may have been pre-registered by an attacker
        if (userRepository.existsByEmailIgnoreCase(googleUser.email())) {
            throw new EmailAlreadyExistsException(
                    "An account with this email already exists. Log in with your password.");
        }
        User user = new User();
        user.setEmail(googleUser.email());
        user.setRole(USER);
        user.setGoogleSub(googleUser.sub());
        return userRepository.save(user);
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
        String storedHash = user.map(User::getPassword).orElse(null);
        // Always pay the bcrypt cost, even when there is no account (or no password, for
        // Google-only accounts) - otherwise a fast "no" reveals that the email isn't registered.
        boolean passwordMatches = bCryptPasswordEncoder.matches(password, storedHash != null ? storedHash : dummyPasswordHash);
        if (user.isPresent() && storedHash != null && passwordMatches) {
            log.info("Successfully authenticated user id: {}", user.get().getUserId());
            return user;
        }
        return Optional.empty();
    }

    public String createRefreshToken(User user, UUID familyId) {

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);


        String rawToken = TokenHashing.generateRawToken();
        //hash that raw string deterministically. The same input always produces the same output, which lets you look up refresh token later.
        String tokenHash = TokenHashing.sha256(rawToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setFamilyId(familyId);
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(expiryDate);
        refreshTokenRepository.save(refreshToken);

        return rawToken;

    }

    // Deliberately NOT @Transactional: this method runs two separate transactions one after
    // the other (rotate, then - only on reuse - revoke the family). Nesting the second inside
    // the first would make every rejected request hold one DB connection while waiting for
    // another, so a burst of replayed tokens could exhaust the connection pool.
    public LoginResponseDTO refresh(String rawRefreshToken) {
        String hashRefreshToken = TokenHashing.sha256(rawRefreshToken);
        AtomicReference<UUID> reusedFamilyId = new AtomicReference<>();

        // transaction 1: validate the token and rotate it
        LoginResponseDTO result = requiresNewTransactionTemplate.execute(status -> {
            RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashRefreshToken).orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found in database!"));

            if (refreshToken.isRevoked()) { //check if its revoked
                reusedFamilyId.set(refreshToken.getFamilyId());
                return null;
            }
            if (refreshToken.getExpiryDate().before(new Date())) { //check expiry date
                throw new InvalidRefreshTokenException("refreshToken is expired!");
            }

            // The isRevoked() check above reads a snapshot, so two simultaneous requests with the
            // same token can both pass it. This conditional UPDATE is the real single-use guard:
            // only one request can flip revoked false -> true; the loser gets 0 rows and is
            // treated as reuse, exactly like a replayed token.
            if (refreshTokenRepository.revokeIfActive(refreshToken.getTokenId()) == 0) {
                reusedFamilyId.set(refreshToken.getFamilyId());
                return null;
            }

            String newRefreshToken = createRefreshToken(refreshToken.getUser(), refreshToken.getFamilyId());
            String newAccessToken = generateToken(refreshToken.getUser());

            return new LoginResponseDTO(newAccessToken, newRefreshToken, refreshToken.getUser().getEmail(), refreshToken.getUser().getUserId());
        });

        if (result == null) {
            // transaction 2: reuse detected - revoke the whole family. It commits on its own
            // before the exception is thrown, so the rejection can't roll the revocation back.
            UUID familyId = reusedFamilyId.get();
            requiresNewTransactionTemplate.executeWithoutResult(status ->
                    refreshTokenRepository.revokeAllByFamilyId(familyId));
            throw new InvalidRefreshTokenException("refreshToken is revoked!");
        }
        return result;
    }

    @Transactional
    public void logout(String jti, Date accessTokenExpiry, String rawRefreshToken) {
        // jti is null when the caller had no usable access token (missing or already expired)
        if (jti != null) {
            accessTokenDenylist.revoke(jti, accessTokenExpiry);
        }

        if (rawRefreshToken != null) {
            String tokenHash = TokenHashing.sha256(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(refreshToken -> {
                refreshToken.setRevoked(true);
                refreshTokenRepository.save(refreshToken);
            });
        }
    }
}

