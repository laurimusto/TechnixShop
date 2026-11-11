package com.controller;


import com.dto.user.AuthResponseDTO;
import com.dto.user.LoginRequestDTO;
import com.dto.user.LoginResponseDTO;
import com.dto.user.RegisterRequestDTO;
import com.exception.CaptchaVerificationException;
import com.security.CaptchaVerifier;
import com.service.AuthService;
import io.jsonwebtoken.Claims;
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

    @Value("${refresh.expiration}")
    private long refreshExpirationMs;

    public AuthController(AuthService authService, CaptchaVerifier captchaVerifier) {
        this.authService = authService;
        this.captchaVerifier = captchaVerifier;
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

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(@CookieValue("refreshToken") String rawRefreshToken) {
        LoginResponseDTO result = authService.refresh(rawRefreshToken);

        ResponseCookie cookie = buildRefreshCookie(result.refreshToken(), Duration.ofMillis(refreshExpirationMs));

        AuthResponseDTO body = new AuthResponseDTO(result.token(), result.email(), result.id());

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorizationHeader,
                                        @CookieValue(value = "refreshToken", required = false) String rawRefreshToken) {
        String accessToken = authorizationHeader.substring(7); // strip "Bearer "
        Claims claims = authService.parseToken(accessToken);

        authService.logout(claims.getId(), claims.getExpiration(), rawRefreshToken);

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
