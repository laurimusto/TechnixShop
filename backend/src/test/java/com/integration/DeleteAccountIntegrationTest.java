package com.integration;

import com.dto.user.AuthResponseDTO;
import com.dto.user.DeleteAccountRequestDTO;
import com.dto.user.GoogleLoginRequestDTO;
import com.dto.user.LoginRequestDTO;
import com.dto.user.RegisterRequestDTO;
import com.entity.User;
import com.repository.RefreshTokenRepository;
import com.repository.UserRepository;
import com.security.GoogleTokenVerifier.GoogleUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// DELETE /me: a logged-in user deletes their own account after re-authenticating.
class DeleteAccountIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "password123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String uniqueEmail() {
        return "d" + UUID.randomUUID().toString().substring(0, 8) + "@ex.com";
    }

    private String tokenOf(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class).token();
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequestDTO(email, PASSWORD, PASSWORD, "test-captcha-token", null))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return tokenOf(result);
    }

    // creates a Google-only account; the mocked verifier maps the given idToken to this Google user
    private String googleSignUp(String idToken, String sub, String email) throws Exception {
        when(googleTokenVerifier.verify(idToken)).thenReturn(Optional.of(new GoogleUser(sub, email, true)));
        MvcResult result = mockMvc.perform(post("/googlelogin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequestDTO(idToken))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return tokenOf(result);
    }

    private int deleteMe(String token, DeleteAccountRequestDTO body) throws Exception {
        var request = delete("/me").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("without a token the request is rejected with 401")
    void withoutToken_returns401() throws Exception {
        assertThat(deleteMe(null, new DeleteAccountRequestDTO(PASSWORD, null))).isEqualTo(401);
    }

    @Test
    @DisplayName("wrong or missing password: 400 and the account still exists")
    void wrongPassword_returns400AndKeepsAccount() throws Exception {
        String email = uniqueEmail();
        String token = register(email);

        assertThat(deleteMe(token, new DeleteAccountRequestDTO("notMyPassword", null))).isEqualTo(400);
        assertThat(deleteMe(token, new DeleteAccountRequestDTO(null, null))).isEqualTo(400);
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    @DisplayName("correct password: 204, user and refresh tokens are gone, login no longer works")
    void correctPassword_deletesUserAndTokens() throws Exception {
        String email = uniqueEmail();
        String token = register(email);
        User user = userRepository.findByEmail(email).orElseThrow();

        assertThat(deleteMe(token, new DeleteAccountRequestDTO(PASSWORD, null))).isEqualTo(204);

        assertThat(userRepository.findByEmail(email)).isEmpty();
        // removed by ON DELETE CASCADE in the database, not by Java code
        assertThat(refreshTokenRepository.findByUser(user)).isEmpty();
        int login = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, PASSWORD))))
                .andReturn().getResponse().getStatus();
        assertThat(login).isEqualTo(401);
    }

    @Test
    @DisplayName("after deletion, the still-unexpired access token is rejected with 401 (not 404)")
    void afterDeletion_oldAccessTokenRejected() throws Exception {
        String token = register(uniqueEmail());

        assertThat(deleteMe(token, new DeleteAccountRequestDTO(PASSWORD, null))).isEqualTo(204);

        int status = mockMvc.perform(get("/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("Google-only account: a fresh sign-in with the same Google account deletes it")
    void googleAccount_sameGoogleUser_deletes() throws Exception {
        String email = uniqueEmail();
        String sub = UUID.randomUUID().toString();
        String token = googleSignUp("signup-token", sub, email);
        when(googleTokenVerifier.verify("fresh-token")).thenReturn(Optional.of(new GoogleUser(sub, email, true)));

        assertThat(deleteMe(token, new DeleteAccountRequestDTO(null, "fresh-token"))).isEqualTo(204);
        assertThat(userRepository.findByGoogleSub(sub)).isEmpty();
    }

    @Test
    @DisplayName("Google-only account: a different Google account, an invalid token or none at all is rejected")
    void googleAccount_wrongProof_returns400() throws Exception {
        String sub = UUID.randomUUID().toString();
        String token = googleSignUp("signup-token-2", sub, uniqueEmail());
        when(googleTokenVerifier.verify("other-account-token"))
                .thenReturn(Optional.of(new GoogleUser(UUID.randomUUID().toString(), uniqueEmail(), true)));
        when(googleTokenVerifier.verify("forged-token")).thenReturn(Optional.empty());

        assertThat(deleteMe(token, new DeleteAccountRequestDTO(null, "other-account-token"))).isEqualTo(400);
        assertThat(deleteMe(token, new DeleteAccountRequestDTO(null, "forged-token"))).isEqualTo(400);
        assertThat(deleteMe(token, new DeleteAccountRequestDTO(null, null))).isEqualTo(400);
        assertThat(userRepository.findByGoogleSub(sub)).isPresent();
    }
}
