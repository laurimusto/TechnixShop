package com.controller;


import com.dto.user.*;
import com.exception.CaptchaVerificationException;
import com.exception.InvalidCredentialsException;
import com.security.CaptchaVerifier;
import com.security.GoogleTokenVerifier;
import com.security.GoogleTokenVerifier.GoogleUser;
import com.service.AuthService;
import com.service.PasswordResetService;
import com.exception.InvalidRefreshTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
public class AuthController {

    private final AuthService authService;
    private final CaptchaVerifier captchaVerifier;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final PasswordResetService passwordResetService;

    @Value("${refresh.expiration}")
    private long refreshExpirationMs;

    public AuthController(AuthService authService, CaptchaVerifier captchaVerifier, GoogleTokenVerifier googleTokenVerifier, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.captchaVerifier = captchaVerifier;
        this.googleTokenVerifier = googleTokenVerifier;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody @Valid RegisterRequestDTO registerRequestDTO) {
        if (!captchaVerifier.verify(registerRequestDTO.captchaToken())) {
            throw new CaptchaVerificationException("CAPTCHA verification failed");
        }

        LoginResponseDTO result = authService.createUser(registerRequestDTO);
        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), Duration.ofMillis(refreshExpirationMs));

        AuthResponseDTO body = new AuthResponseDTO(result.token(), result.email(), result.id());

        return ResponseEntity.status(HttpStatus.CREATED) //returning ResponseEntity, it ignores @ResponseStatus
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody @Valid LoginRequestDTO loginRequestDTO) {
        LoginResponseDTO result = authService.login(loginRequestDTO.email(), loginRequestDTO.password());

        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), Duration.ofMillis(refreshExpirationMs));

        AuthResponseDTO body = new AuthResponseDTO(result.token(), result.email(), result.id());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);

    }

    @PostMapping("/googlelogin")
    public ResponseEntity<AuthResponseDTO> googleLogin(@RequestBody @Valid GoogleLoginRequestDTO request) {
        GoogleUser googleUser = googleTokenVerifier.verify(request.idToken())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid Google sign-in"));

        LoginResponseDTO result = authService.googleLogin(googleUser);

        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), Duration.ofMillis(refreshExpirationMs));

        AuthResponseDTO body = new AuthResponseDTO(result.token(), result.email(), result.id());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO request) {
        passwordResetService.requestReset(request.email());

        // always the same empty 202, whether the email exists (no user enumeration)
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@CookieValue(value = "refreshToken", required = false) String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }
        LoginResponseDTO result = authService.refresh(rawRefreshToken);

        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), Duration.ofMillis(refreshExpirationMs));

        AuthResponseDTO body = new AuthResponseDTO(result.token(), result.email(), result.id());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                       @CookieValue(value = "refreshToken", required = false) String rawRefreshToken) {
        // Logout must always succeed in ending the session, even when the access token is
        // missing, malformed or expired: such a token can't be used anyway, so there is nothing
        // to denylist - but the refresh token still has to be revoked and the cookie cleared.
        Claims claims = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            try {
                claims = authService.parseToken(authorizationHeader.substring(7)); // strip "Bearer "
            } catch (JwtException | IllegalArgumentException e) {
                // unusable access token: fall through and revoke only the refresh token
            }
        }

        if (claims != null) {
            authService.logout(claims.getId(), claims.getExpiration(), rawRefreshToken);
        } else {
            authService.logout(null, null, rawRefreshToken);
        }

        ResponseCookie expiredCookie = buildRefreshCookie("", Duration.ZERO);

        return ResponseEntity
                .noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .build();
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
