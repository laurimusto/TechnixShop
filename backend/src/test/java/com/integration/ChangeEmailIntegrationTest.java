package com.integration;

import com.dto.user.AuthResponseDTO;
import com.dto.user.ChangeEmailRequestDTO;
import com.dto.user.ConfirmEmailRequestDTO;
import com.dto.user.GoogleLoginRequestDTO;
import com.dto.user.LoginRequestDTO;
import com.dto.user.RegisterRequestDTO;
import com.entity.EmailChangeToken;
import com.entity.User;
import com.repository.EmailChangeTokenRepository;
import com.repository.UserRepository;
import com.security.GoogleTokenVerifier.GoogleUser;
import com.security.TokenHashing;
import com.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// End-to-end tests for changing the email: POST /me/email -> link in the new inbox -> POST /confirm-email.
class ChangeEmailIntegrationTest extends IntegrationTestBase {

    // the raw token only exists in the email, so the test "reads the email" by capturing the argument
    @MockitoBean
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EmailChangeTokenRepository emailChangeTokenRepository;

    private static final String PASSWORD = "password123";

    private String uniqueEmail() {
        return "e" + UUID.randomUUID().toString().substring(0, 8) + "@ex.com";
    }

    // registers and returns the access token
    private String register(String email) throws Exception {
        String body = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequestDTO(email, PASSWORD, PASSWORD, "test-captcha-token"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, AuthResponseDTO.class).token();
    }

    private int requestChange(String accessToken, String newEmail, String password) throws Exception {
        return mockMvc.perform(post("/me/email")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeEmailRequestDTO(newEmail, password))))
                .andReturn().getResponse().getStatus();
    }

    private String captureToken(String newEmail) {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailChangeConfirmation(eq(newEmail), tokenCaptor.capture());
        return tokenCaptor.getValue();
    }

    private int confirm(String token) throws Exception {
        return mockMvc.perform(post("/confirm-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmEmailRequestDTO(token))))
                .andReturn().getResponse().getStatus();
    }

    private int login(String email) throws Exception {
        return mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, PASSWORD))))
                .andReturn().getResponse().getStatus();
    }

    private boolean hasPendingToken(String email) {
        Long userId = userRepository.findByEmail(email).orElseThrow().getUserId();
        return emailChangeTokenRepository.findAll().stream().anyMatch(t -> t.getUser().getUserId().equals(userId));
    }

    @Test
    @DisplayName("1. full flow: 202, nothing changes until the link is clicked, then only the new email logs in")
    void fullFlow_changesEmailOnlyAfterConfirm() throws Exception {
        String oldEmail = uniqueEmail();
        String newEmail = uniqueEmail();
        String accessToken = register(oldEmail);

        assertThat(requestChange(accessToken, newEmail, PASSWORD)).isEqualTo(202);
        String token = captureToken(newEmail);
        verify(emailService).sendEmailChangeNotice(oldEmail, newEmail);

        // before the click: still the old email
        assertThat(login(oldEmail)).isEqualTo(200);
        assertThat(login(newEmail)).isEqualTo(401);

        assertThat(confirm(token)).isEqualTo(204);

        assertThat(login(newEmail)).isEqualTo(200);
        assertThat(login(oldEmail)).isEqualTo(401);
        // the session stays valid: the JWT is tied to the user id, not the email
        String me = mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getContentAsString();
        assertThat(me).contains(newEmail);
    }

    @Test
    @DisplayName("2. not logged in: 401")
    void noAuth_returns401() throws Exception {
        int status = mockMvc.perform(post("/me/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeEmailRequestDTO(uniqueEmail(), PASSWORD))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("3. wrong password: 400 and no email is sent")
    void wrongPassword_returns400() throws Exception {
        String accessToken = register(uniqueEmail());

        assertThat(requestChange(accessToken, uniqueEmail(), "wrongPassword")).isEqualTo(400);
        verify(emailService, never()).sendEmailChangeConfirmation(anyString(), anyString());
    }

    @Test
    @DisplayName("4. the same email as now (any case): 400")
    void sameEmail_returns400() throws Exception {
        String email = uniqueEmail();
        String accessToken = register(email);

        assertThat(requestChange(accessToken, email.toUpperCase(), PASSWORD)).isEqualTo(400);
    }

    @Test
    @DisplayName("5. an email another account already uses: 409")
    void takenEmail_returns409() throws Exception {
        String taken = uniqueEmail();
        register(taken);
        String accessToken = register(uniqueEmail());

        assertThat(requestChange(accessToken, taken, PASSWORD)).isEqualTo(409);
    }

    @Test
    @DisplayName("6. malformed email: 400 from validation")
    void malformedEmail_returns400() throws Exception {
        String accessToken = register(uniqueEmail());

        assertThat(requestChange(accessToken, "not-an-email", PASSWORD)).isEqualTo(400);
    }

    @Test
    @DisplayName("7. Google-only account: 400 - its email comes from Google")
    void googleOnlyAccount_returns400() throws Exception {
        String email = uniqueEmail();
        when(googleTokenVerifier.verify("google-token"))
                .thenReturn(Optional.of(new GoogleUser(UUID.randomUUID().toString(), email, true)));
        String body = mockMvc.perform(post("/googlelogin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequestDTO("google-token"))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readValue(body, AuthResponseDTO.class).token();

        assertThat(requestChange(accessToken, uniqueEmail(), "anything")).isEqualTo(400);
    }

    @Test
    @DisplayName("8. a link works only once")
    void tokenUsedTwice_secondRejected() throws Exception {
        String newEmail = uniqueEmail();
        String accessToken = register(uniqueEmail());
        requestChange(accessToken, newEmail, PASSWORD);
        String token = captureToken(newEmail);

        assertThat(confirm(token)).isEqualTo(204);
        assertThat(confirm(token)).isEqualTo(400);
    }

    @Test
    @DisplayName("9. made-up or blank token: 400")
    void unknownToken_returns400() throws Exception {
        assertThat(confirm("this-token-was-never-issued")).isEqualTo(400);
        assertThat(confirm("")).isEqualTo(400);
    }

    @Test
    @DisplayName("10. expired link: 400 and the email stays the same")
    void expiredToken_returns400() throws Exception {
        String email = uniqueEmail();
        register(email);
        User user = userRepository.findByEmail(email).orElseThrow();

        // insert an already expired token - waiting an hour isn't an option
        String rawToken = TokenHashing.generateRawToken();
        EmailChangeToken expired = new EmailChangeToken();
        expired.setTokenHash(TokenHashing.sha256(rawToken));
        expired.setUser(user);
        expired.setNewEmail(uniqueEmail());
        expired.setExpiryDate(new Date(System.currentTimeMillis() - 60_000));
        emailChangeTokenRepository.save(expired);

        assertThat(confirm(rawToken)).isEqualTo(400);
        assertThat(login(email)).isEqualTo(200);
    }

    @Test
    @DisplayName("11. new email registered by someone else before the click: 409, nothing changes")
    void emailTakenBeforeConfirm_returns409() throws Exception {
        String oldEmail = uniqueEmail();
        String newEmail = uniqueEmail();
        String accessToken = register(oldEmail);
        requestChange(accessToken, newEmail, PASSWORD);
        String token = captureToken(newEmail);

        register(newEmail); // someone else grabs the address in between

        assertThat(confirm(token)).isEqualTo(409);
        assertThat(login(oldEmail)).isEqualTo(200);
    }

    @Test
    @DisplayName("12. a newer request replaces the older one: the first link stops working")
    void secondRequest_invalidatesFirstLink() throws Exception {
        String first = uniqueEmail();
        String second = uniqueEmail();
        String accessToken = register(uniqueEmail());

        requestChange(accessToken, first, PASSWORD);
        String firstToken = captureToken(first);
        requestChange(accessToken, second, PASSWORD);
        String secondToken = captureToken(second);

        assertThat(confirm(firstToken)).isEqualTo(400);
        assertThat(confirm(secondToken)).isEqualTo(204);
    }

    @Test
    @DisplayName("13. mail server down: 503 and no pending token is left behind (atomicity)")
    void mailFailure_rollsBackToken() throws Exception {
        String email = uniqueEmail();
        String accessToken = register(email);
        doThrow(new MailSendException("mail server down"))
                .when(emailService).sendEmailChangeConfirmation(anyString(), anyString());

        assertThat(requestChange(accessToken, uniqueEmail(), PASSWORD)).isEqualTo(503);
        assertThat(hasPendingToken(email)).isFalse();
    }
}
