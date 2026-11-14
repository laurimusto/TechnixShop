package com.service;

import com.dto.user.LoginResponseDTO;
import com.dto.user.RegisterRequestDTO;
import com.entity.RefreshToken;
import com.entity.User;
import com.enums.Role;
import com.exception.EmailAlreadyExistsException;
import com.exception.InvalidCredentialsException;
import com.exception.InvalidEmailException;
import com.exception.InvalidRefreshTokenException;
import com.exception.PasswordMismatchException;
import com.exception.PasswordTooShortException;
import com.repository.RefreshTokenRepository;
import com.repository.UserRepository;
import com.security.AccessTokenDenylist;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long JWT_EXPIRATION_MS = 1_800_000L; // 30 min, same shape as application.properties
    private static final long REFRESH_EXPIRATION_MS = 432_000_000L; // 5 days

    @Mock
    private UserRepository userRepository;
    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AccessTokenDenylist accessTokenDenylist;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AuthService authService;

    // @Value fields are never populated outside a Spring context, so they're set manually here
    // to mirror what application.properties would normally inject at runtime.
    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, bCryptPasswordEncoder, refreshTokenRepository, accessTokenDenylist, transactionManager);
        ReflectionTestUtils.setField(authService, "jwtSecret", "unit-test-only-secret-key-that-is-long-enough-for-hs256");
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", JWT_EXPIRATION_MS);
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", REFRESH_EXPIRATION_MS);
    }

    private User testUser() {
        // setters instead of the all-args constructor, so new User fields don't break this
        User user = new User();
        user.setUserId(1L);
        user.setEmail("test@example.com");
        user.setPassword("hashed-password");
        user.setRole(Role.USER);
        return user;
    }

    @Test
    @DisplayName("generateToken: includes subject, role, a unique jti, and an expiry ~jwtExpirationMs away")
    void generateToken_includesSubjectRoleJtiAndExpiry() {
        User user = testUser();

        String token = authService.generateToken(user);
        Claims claims = authService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getId()).isNotBlank();

        long expectedExpiry = System.currentTimeMillis() + JWT_EXPIRATION_MS;
        assertThat(claims.getExpiration().getTime()).isCloseTo(expectedExpiry, org.assertj.core.data.Offset.offset(5_000L));
    }

    @Test
    @DisplayName("generateToken: two tokens for the same user get different jti values")
    void generateToken_producesUniqueJtiPerCall() {
        User user = testUser();

        String tokenA = authService.generateToken(user);
        String tokenB = authService.generateToken(user);

        String jtiA = authService.parseToken(tokenA).getId();
        String jtiB = authService.parseToken(tokenB).getId();

        assertThat(jtiA).isNotEqualTo(jtiB);
    }

    @Test
    @DisplayName("parseToken: rejects a token whose signature has been tampered with")
    void parseToken_rejectsTamperedToken() {
        String token = authService.generateToken(testUser());
        String tampered = token + "tampered";

        assertThatThrownBy(() -> authService.parseToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parseToken: rejects a token that has already expired")
    void parseToken_rejectsExpiredToken() {
        ReflectionTestUtils.setField(authService, "jwtExpirationMs", -1_000L);
        String expiredToken = authService.generateToken(testUser());

        assertThatThrownBy(() -> authService.parseToken(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("refresh: rotates the token - old one is revoked, new one keeps the same familyId")
    void refresh_rotatesTokenWithinSameFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken existingToken = new RefreshToken();
        existingToken.setFamilyId(familyId);
        existingToken.setUser(testUser());
        existingToken.setRevoked(false);
        existingToken.setExpiryDate(new Date(System.currentTimeMillis() + 100_000));

        existingToken.setTokenId(42L);

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));
        when(refreshTokenRepository.revokeIfActive(42L)).thenReturn(1); // this request won the atomic claim

        LoginResponseDTO result = authService.refresh("raw-refresh-token");

        // the old token is revoked by the atomic UPDATE, so only the new token goes through save()
        verify(refreshTokenRepository).revokeIfActive(42L);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken savedNew = captor.getValue();
        assertThat(savedNew.getFamilyId()).isEqualTo(familyId);
        assertThat(savedNew.isRevoked()).isFalse();
        assertThat(result.email()).isEqualTo(testUser().getEmail());
    }

    @Test
    @DisplayName("refresh: losing a concurrent race for the same token throws, issues nothing, and revokes the family")
    void refresh_losingConcurrentRaceRevokesWholeFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken existingToken = new RefreshToken();
        existingToken.setTokenId(42L);
        existingToken.setFamilyId(familyId);
        existingToken.setUser(testUser());
        existingToken.setRevoked(false); // looked active when read...
        existingToken.setExpiryDate(new Date(System.currentTimeMillis() + 100_000));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));
        // ...but another request revoked it first, so the conditional UPDATE matches 0 rows
        when(refreshTokenRepository.revokeIfActive(42L)).thenReturn(0);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        assertThatThrownBy(() -> authService.refresh("raced-raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeAllByFamilyId(familyId);
        verify(refreshTokenRepository, times(0)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refresh: reusing an already-revoked token throws and revokes the whole family")
    void refresh_reuseOfRevokedTokenRevokesWholeFamily() {
        UUID familyId = UUID.randomUUID();
        RefreshToken revokedToken = new RefreshToken();
        revokedToken.setFamilyId(familyId);
        revokedToken.setUser(testUser());
        revokedToken.setRevoked(true);
        revokedToken.setExpiryDate(new Date(System.currentTimeMillis() + 100_000));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));
        // the revocation runs in a forced new transaction (see AuthService.refresh) - the mocked
        // transaction manager just needs to hand back a real, lightweight status object so
        // TransactionTemplate's executeWithoutResult(...) has something to commit.
        // lenient: if a future change stops using the REQUIRES_NEW transaction, this test should
        // still report on revocation behaviour, not fail on an unused-stub technicality.
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());

        assertThatThrownBy(() -> authService.refresh("stolen-raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeAllByFamilyId(familyId);
    }

    @Test
    @DisplayName("refresh: an expired (but not revoked) token is rejected")
    void refresh_rejectsExpiredToken() {
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setUser(testUser());
        expiredToken.setRevoked(false);
        expiredToken.setExpiryDate(new Date(System.currentTimeMillis() - 1_000));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.refresh("expired-raw-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("refresh: an unknown token (not found in DB) is rejected")
    void refresh_rejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("logout: revokes the access token's jti in the denylist")
    void logout_revokesAccessTokenInDenylist() {
        Date expiry = new Date();

        authService.logout("jti-123", expiry, null);

        verify(accessTokenDenylist).revoke(eq("jti-123"), eq(expiry));
    }

    @Test
    @DisplayName("logout: marks the matching refresh token as revoked when a cookie value is given")
    void logout_marksRefreshTokenRevoked_whenCookiePresent() {
        RefreshToken existingToken = new RefreshToken();
        existingToken.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));

        authService.logout("jti-123", new Date(), "raw-refresh-token");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertThat(captor.getValue().isRevoked()).isTrue();
    }

    @Test
    @DisplayName("logout: does not touch the refresh token repository when no cookie was sent")
    void logout_skipsRefreshTokenLookup_whenNoCookie() {
        authService.logout("jti-123", new Date(), null);

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    @DisplayName("createUser: rejects an email that's already registered")
    void createUser_rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);

        RegisterRequestDTO request = new RegisterRequestDTO("test@example.com", "password", "password", "test-captcha-token");

        assertThatThrownBy(() -> authService.createUser(request))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("createUser: rejects mismatched password/repeatPassword")
    void createUser_rejectsPasswordMismatch() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        RegisterRequestDTO request = new RegisterRequestDTO("test@example.com", "password", "different", "test-captcha-token");

        assertThatThrownBy(() -> authService.createUser(request))
                .isInstanceOf(PasswordMismatchException.class);
    }

    @Test
    @DisplayName("createUser: rejects a password shorter than 3 characters")
    void createUser_rejectsTooShortPassword() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        RegisterRequestDTO request = new RegisterRequestDTO("test@example.com", "ab", "ab", "test-captcha-token");

        assertThatThrownBy(() -> authService.createUser(request))
                .isInstanceOf(PasswordTooShortException.class);
    }

    @Test
    @DisplayName("createUser: rejects an email longer than 50 characters")
    void createUser_rejectsTooLongEmail() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        String longEmail = "a".repeat(45) + "@test.com"; // > 50 chars total

        RegisterRequestDTO request = new RegisterRequestDTO(longEmail, "password", "password", "test-captcha-token");

        assertThatThrownBy(() -> authService.createUser(request))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    @DisplayName("createUser: happy path hashes the password, assigns USER role, and returns both tokens")
    void createUser_happyPath() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(bCryptPasswordEncoder.encode("password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(1L);
            return user;
        });

        RegisterRequestDTO request = new RegisterRequestDTO("test@example.com", "password", "password", "test-captcha-token");
        LoginResponseDTO result = authService.createUser(request);

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());

        assertThat(savedUserCaptor.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(savedUserCaptor.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.token()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("login: valid credentials return both tokens")
    void login_happyPath() {
        User user = testUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(bCryptPasswordEncoder.matches("password", "hashed-password")).thenReturn(true);

        LoginResponseDTO result = authService.login("test@example.com", "password");

        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.token()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("login: wrong password is rejected")
    void login_rejectsWrongPassword() {
        User user = testUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(bCryptPasswordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("test@example.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login: unknown email is rejected")
    void login_rejectsUnknownEmail() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("unknown@example.com", "password"))
                .isInstanceOf(InvalidCredentialsException.class);

        // bcrypt must still run (against a dummy hash), so the response time doesn't
        // reveal that the email isn't registered
        verify(bCryptPasswordEncoder).matches(eq("password"), any());
    }

    @Test
    @DisplayName("logout: with no usable access token (null jti) still revokes the refresh token, and skips the denylist")
    void logout_withoutAccessToken_stillRevokesRefreshToken() {
        RefreshToken existingToken = new RefreshToken();
        existingToken.setRevoked(false);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(existingToken));

        authService.logout(null, null, "raw-refresh-token");

        assertThat(existingToken.isRevoked()).isTrue();
        verifyNoInteractions(accessTokenDenylist);
    }

    @Test
    @DisplayName("authenticate: correct email and password returns the user")
    void authenticate_correctCredentialsReturnsUser() {
        User user = testUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(bCryptPasswordEncoder.matches("password", "hashed-password")).thenReturn(true);

        Optional<User> result = authService.authenticate("test@example.com", "password");

        assertThat(result).contains(user);
    }

    @Test
    @DisplayName("authenticate: wrong password returns empty, not an exception")
    void authenticate_wrongPasswordReturnsEmpty() {
        User user = testUser();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(bCryptPasswordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        assertThat(authService.authenticate("test@example.com", "wrong")).isEmpty();
    }

    @Test
    @DisplayName("authenticate: unknown email returns empty, not an exception")
    void authenticate_unknownEmailReturnsEmpty() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThat(authService.authenticate("unknown@example.com", "password")).isEmpty();
    }
}
