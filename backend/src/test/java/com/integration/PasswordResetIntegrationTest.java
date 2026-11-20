package com.integration;

import com.dto.user.AuthResponseDTO;
import com.dto.user.ForgotPasswordRequestDTO;
import com.dto.user.GoogleLoginRequestDTO;
import com.dto.user.LoginRequestDTO;
import com.dto.user.RegisterRequestDTO;
import com.dto.user.ResetPasswordRequestDTO;
import com.entity.PasswordResetToken;
import com.entity.User;
import com.repository.PasswordResetTokenRepository;
import com.repository.UserRepository;
import com.security.GoogleTokenVerifier.GoogleUser;
import com.security.TokenHashing;
import com.service.EmailService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// End-to-end tests for the password reset flow: /forgot-password -> email -> /reset-password,
// and the security guarantee that a reset ends every existing session.
class PasswordResetIntegrationTest extends IntegrationTestBase {

    // The raw token only ever exists in the email. Mocking EmailService lets the test
    // "read the email" by capturing the argument, without a real mail server.
    @MockitoBean
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private static final String OLD_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "brandNewPassword456";

    private String uniqueEmail() {
        return "r" + UUID.randomUUID().toString().substring(0, 8) + "@ex.com";
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequestDTO(email, OLD_PASSWORD, OLD_PASSWORD, "test-captcha-token"))))
                .andReturn();
    }

    private MvcResult forgotPassword(String email) throws Exception {
        return mockMvc.perform(post("/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequestDTO(email))))
                .andReturn();
    }

    // requestReset is @Async, so wait (up to 5s) for the background thread to "send" the email,
    // then pull the raw token out of the call.
    private String requestResetAndCaptureToken(String email) throws Exception {
        forgotPassword(email);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(5000)).sendPasswordResetEmail(eq(email), tokenCaptor.capture());
        return tokenCaptor.getValue();
    }

    private int resetPassword(String token, String newPassword, String repeatPassword) throws Exception {
        return mockMvc.perform(post("/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordRequestDTO(token, newPassword, repeatPassword))))
                .andReturn().getResponse().getStatus();
    }

    private int login(String email, String password) throws Exception {
        return mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, password))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("1. valid token: 204, the new password works and the old one no longer does")
    void validToken_changesPassword() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = requestResetAndCaptureToken(email);

        assertThat(resetPassword(token, NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(204);

        assertThat(login(email, NEW_PASSWORD)).isEqualTo(200);
        assertThat(login(email, OLD_PASSWORD)).isEqualTo(401);
    }

    @Test
    @DisplayName("2. unknown email: same 202 as a real one, and no email is ever sent (no user enumeration)")
    void unknownEmail_returns202AndSendsNothing() throws Exception {
        MvcResult result = forgotPassword(uniqueEmail());

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        // give the async thread time to run, then confirm it did nothing
        verify(emailService, after(500).never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("3. after a reset, an access token issued before it is rejected with 401")
    void reset_rejectsOldAccessToken() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = register(email);
        String oldAccessToken = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(), AuthResponseDTO.class).token();

        // iat and passwordChangedAt both have whole-second precision, and a token from the
        // same second as the reset is deliberately allowed. Wait so the old token is truly older.
        Thread.sleep(1100);

        String token = requestResetAndCaptureToken(email);
        assertThat(resetPassword(token, NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(204);

        MvcResult rejected = mockMvc.perform(get("/categories")
                .header("Authorization", "Bearer " + oldAccessToken)).andReturn();
        assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("4. after a reset, a refresh token issued before it is rejected")
    void reset_revokesOldRefreshToken() throws Exception {
        String email = uniqueEmail();
        Cookie oldRefreshCookie = register(email).getResponse().getCookie("refreshToken");
        String token = requestResetAndCaptureToken(email);

        assertThat(resetPassword(token, NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(204);

        MvcResult rejected = mockMvc.perform(post("/refresh").cookie(oldRefreshCookie)).andReturn();
        assertThat(rejected.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("5. a reset link works only once - the second use is rejected with 400")
    void tokenUsedTwice_secondAttemptRejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = requestResetAndCaptureToken(email);

        assertThat(resetPassword(token, NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(204);
        assertThat(resetPassword(token, "anotherPassword789", "anotherPassword789")).isEqualTo(400);
    }

    @Test
    @DisplayName("6. an expired token is rejected with 400 and the password stays unchanged")
    void expiredToken_rejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        User user = userRepository.findByEmail(email).orElseThrow();

        // insert a token directly, already expired - waiting 30 real minutes isn't an option
        String rawToken = TokenHashing.generateRawToken();
        PasswordResetToken expired = new PasswordResetToken();
        expired.setTokenHash(TokenHashing.sha256(rawToken));
        expired.setUser(user);
        expired.setExpiryDate(new Date(System.currentTimeMillis() - 60_000));
        passwordResetTokenRepository.save(expired);

        assertThat(resetPassword(rawToken, NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(400);
        assertThat(login(email, OLD_PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("7. a made-up token is rejected with 400")
    void unknownToken_rejected() throws Exception {
        assertThat(resetPassword("this-token-was-never-issued", NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(400);
    }

    @Test
    @DisplayName("8. mismatched passwords: 400, and the token is not burned - it still works afterwards")
    void passwordMismatch_rejectedButTokenStillUsable() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = requestResetAndCaptureToken(email);

        assertThat(resetPassword(token, NEW_PASSWORD, "typoInRepeat")).isEqualTo(400);
        assertThat(resetPassword(token, NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(204);
    }

    @Test
    @DisplayName("9. Google-only account: same 202, a 'use Google' email instead of a reset link, and no token created")
    void googleOnlyAccount_getsGoogleEmailNotResetLink() throws Exception {
        String email = uniqueEmail();
        when(googleTokenVerifier.verify("google-token"))
                .thenReturn(Optional.of(new GoogleUser(UUID.randomUUID().toString(), email, true)));
        mockMvc.perform(post("/googlelogin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new GoogleLoginRequestDTO("google-token"))));

        assertThat(forgotPassword(email).getResponse().getStatus()).isEqualTo(202);

        verify(emailService, timeout(5000)).sendGoogleAccountEmail(email);
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordResetTokenRepository.findAll())
                .noneMatch(t -> t.getUser().getUserId().equals(user.getUserId()));
    }

    @Test
    @DisplayName("10. mail server down: still 202, and the rollback keeps the previous link valid (no orphan token)")
    void mailFailure_rollsBackAndKeepsPreviousLink() throws Exception {
        String email = uniqueEmail();
        register(email);
        String firstToken = requestResetAndCaptureToken(email);

        // the second request would normally replace the first token, but its email fails to send
        doThrow(new MailSendException("mail server down"))
                .when(emailService).sendPasswordResetEmail(anyString(), anyString());
        assertThat(forgotPassword(email).getResponse().getStatus()).isEqualTo(202);
        verify(emailService, timeout(5000).times(2)).sendPasswordResetEmail(eq(email), anyString());

        // the rollback undid "delete old token + save new one", so the first link still works.
        // (If the async transaction hasn't finished yet, the database makes this wait on its row lock.)
        assertThat(resetPassword(firstToken, NEW_PASSWORD, NEW_PASSWORD)).isEqualTo(204);
        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordResetTokenRepository.findAll())
                .noneMatch(t -> t.getUser().getUserId().equals(user.getUserId()));
    }
}
