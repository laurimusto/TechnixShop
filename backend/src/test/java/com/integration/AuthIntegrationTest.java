package com.integration;

import com.dto.error.ErrorResponseDTO;
import com.dto.user.AuthResponseDTO;
import com.dto.user.GoogleLoginRequestDTO;
import com.dto.user.LoginRequestDTO;
import com.dto.user.RegisterRequestDTO;
import com.repository.RefreshTokenRepository;
import com.repository.UserRepository;
import com.security.GoogleTokenVerifier.GoogleUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class AuthIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String uniqueEmail() {
        return "t" + UUID.randomUUID().toString().substring(0, 8) + "@ex.com"; // must stay under 50 chars
    }

    private MvcResult register(String email, String password) throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO(email, password, password, "test-captcha-token");
        return mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    private Cookie refreshCookieOf(MvcResult result) {
        return result.getResponse().getCookie("refreshToken");
    }

    private AuthResponseDTO authBody(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class);
    }

    private ErrorResponseDTO errorBody(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponseDTO.class);
    }

    @Test
    @DisplayName("register: creates a user with a hashed password and returns a token + HttpOnly refresh cookie")
    void register_createsUserAndReturnsTokenAndCookie() throws Exception {
        String email = uniqueEmail();

        MvcResult result = register(email, "password123");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        AuthResponseDTO body = authBody(result);
        assertThat(body.token()).isNotBlank();
        assertThat(body.email()).isEqualTo(email);

        Cookie cookie = refreshCookieOf(result);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();

        var savedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(savedUser.getPassword()).isNotEqualTo("password123"); // hashed, not plaintext
    }

    @Test
    @DisplayName("register: a duplicate email is rejected with 409")
    void register_duplicateEmail_returns409() throws Exception {
        String email = uniqueEmail();
        register(email, "password123");

        MvcResult result = register(email, "password123");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("register: a blank email is rejected with 400, validated end-to-end through real Spring MVC + GlobalExceptionHandler")
    void register_blankEmail_returns400() throws Exception {
        MvcResult result = register("", "password123");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("login: valid credentials succeed, wrong password is rejected with 401")
    void login_validAndInvalidCredentials() throws Exception {
        String email = uniqueEmail();
        register(email, "password123");

        MvcResult success = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, "password123"))))
                .andReturn();
        assertThat(success.getResponse().getStatus()).isEqualTo(200);

        MvcResult failure = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, "wrong-password"))))
                .andReturn();
        assertThat(failure.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("refresh: rotates the token - the old cookie can never be used again (real DB persistence, not just the response)")
    void refresh_rotatesToken_oldCookieRejectedAfterwards() throws Exception {
        String email = uniqueEmail();
        Cookie oldCookie = refreshCookieOf(register(email, "password123"));
        long tokensBefore = refreshTokenRepository.count();

        MvcResult refreshResult = mockMvc.perform(post("/refresh").cookie(oldCookie)).andReturn();
        assertThat(refreshResult.getResponse().getStatus()).isEqualTo(200);
        Cookie newCookie = refreshCookieOf(refreshResult);
        assertThat(newCookie.getValue()).isNotEqualTo(oldCookie.getValue());
        assertThat(refreshTokenRepository.count()).isEqualTo(tokensBefore + 1); // rotation persisted a new row

        // the old cookie was marked revoked in the DB by the rotation above - reusing it must fail
        MvcResult reuseAttempt = mockMvc.perform(post("/refresh").cookie(oldCookie)).andReturn();
        assertThat(reuseAttempt.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("refresh: reusing an already-rotated (stolen) token revokes the whole family, killing the legitimate newest token too")
    void refresh_reuseOfStolenTokenRevokesWholeFamilyIncludingNewestToken() throws Exception {
        String email = uniqueEmail();
        Cookie originalCookie = refreshCookieOf(register(email, "password123"));

        Cookie legitimateNewestCookie = refreshCookieOf(
                mockMvc.perform(post("/refresh").cookie(originalCookie)).andReturn());

        // attacker replays the original (already-rotated) token
        mockMvc.perform(post("/refresh").cookie(originalCookie)).andReturn();

        // the legitimate user's own newest token must now be dead too - the whole family was revoked
        MvcResult legitimateAttempt = mockMvc.perform(post("/refresh").cookie(legitimateNewestCookie)).andReturn();
        assertThat(legitimateAttempt.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("refresh: simultaneous requests with the same token - at most one is rotated, the rest are rejected")
    void refresh_concurrentRequestsWithSameToken_onlyOneSucceeds() throws Exception {
        Cookie cookie = refreshCookieOf(register(uniqueEmail(), "password123"));

        int requests = 8;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch startGun = new CountDownLatch(1); // releases all threads at the same instant
        try {
            List<Future<Integer>> statuses = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                statuses.add(pool.submit(() -> {
                    startGun.await();
                    return mockMvc.perform(post("/refresh").cookie(cookie)).andReturn().getResponse().getStatus();
                }));
            }
            startGun.countDown();

            int succeeded = 0;
            for (Future<Integer> status : statuses) {
                int code = status.get(30, TimeUnit.SECONDS);
                assertThat(code).isIn(200, 401);
                if (code == 200) succeeded++;
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("logout: revokes both the access token (rejected on next use) and the refresh token (rejected on next refresh)")
    void logout_revokesAccessTokenAndRefreshToken() throws Exception {
        String email = uniqueEmail();
        MvcResult registerResult = register(email, "password123");
        String accessToken = authBody(registerResult).token();
        Cookie refreshCookie = refreshCookieOf(registerResult);

        mockMvc.perform(post("/logout")
                .cookie(refreshCookie)
                .header("Authorization", "Bearer " + accessToken)).andReturn();

        // the revoked access token must now be rejected by JwtAuthenticationFilter on any request
        MvcResult rejectedRequest = mockMvc.perform(get("/categories")
                .header("Authorization", "Bearer " + accessToken)).andReturn();
        assertThat(rejectedRequest.getResponse().getStatus()).isEqualTo(401);

        // the revoked refresh token must also be rejected
        MvcResult rejectedRefresh = mockMvc.perform(post("/refresh").cookie(refreshCookie)).andReturn();
        assertThat(rejectedRefresh.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("logout: an invalid/expired access token (or none at all) still revokes the refresh token and clears the cookie")
    void logout_withUnusableAccessToken_stillRevokesRefreshTokenAndClearsCookie() throws Exception {
        Cookie refreshCookie = refreshCookieOf(register(uniqueEmail(), "password123"));

        MvcResult logoutResult = mockMvc.perform(post("/logout")
                .cookie(refreshCookie)
                .header("Authorization", "Bearer not-a-valid-or-expired-jwt")).andReturn();

        assertThat(logoutResult.getResponse().getStatus()).isEqualTo(204);
        assertThat(refreshCookieOf(logoutResult).getMaxAge()).isZero(); // browser told to delete the cookie

        MvcResult rejectedRefresh = mockMvc.perform(post("/refresh").cookie(refreshCookie)).andReturn();
        assertThat(rejectedRefresh.getResponse().getStatus()).isEqualTo(401);

        // no Authorization header and no cookie at all: nothing to revoke, but not an error
        MvcResult bareLogout = mockMvc.perform(post("/logout")).andReturn();
        assertThat(bareLogout.getResponse().getStatus()).isEqualTo(204);
    }

    private MvcResult googleLogin(String idToken) throws Exception {
        return mockMvc.perform(post("/googlelogin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GoogleLoginRequestDTO(idToken))))
                .andReturn();
    }

    @Test
    @DisplayName("googlelogin: first sign-in creates a passwordless user; a second sign-in reuses that same user")
    void googleLogin_createsUserThenReusesIt() throws Exception {
        String email = uniqueEmail();
        String sub = UUID.randomUUID().toString();
        when(googleTokenVerifier.verify("google-token")).thenReturn(Optional.of(new GoogleUser(sub, email, true)));

        MvcResult first = googleLogin("google-token");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(authBody(first).token()).isNotBlank();
        assertThat(refreshCookieOf(first)).isNotNull();

        var savedUser = userRepository.findByGoogleSub(sub).orElseThrow();
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getPassword()).isNull();

        MvcResult second = googleLogin("google-token");
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(authBody(second).id()).isEqualTo(savedUser.getUserId());
    }

    @Test
    @DisplayName("googlelogin: an email already owned by a password account is rejected with 409, never auto-linked")
    void googleLogin_existingPasswordAccount_returns409AndDoesNotLink() throws Exception {
        String email = uniqueEmail();
        register(email, "password123");
        String sub = UUID.randomUUID().toString();
        when(googleTokenVerifier.verify("google-token")).thenReturn(Optional.of(new GoogleUser(sub, email, true)));

        MvcResult result = googleLogin("google-token");

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(userRepository.findByEmail(email).orElseThrow().getGoogleSub()).isNull();
    }

    @Test
    @DisplayName("googlelogin: a Google account with an unverified email is rejected with 401 and no user is created")
    void googleLogin_unverifiedEmail_returns401() throws Exception {
        String email = uniqueEmail();
        String sub = UUID.randomUUID().toString();
        when(googleTokenVerifier.verify("google-token")).thenReturn(Optional.of(new GoogleUser(sub, email, false)));

        MvcResult result = googleLogin("google-token");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(userRepository.findByGoogleSub(sub)).isEmpty();
    }

    @Test
    @DisplayName("googlelogin: an invalid/forged Google token is rejected with 401")
    void googleLogin_invalidToken_returns401() throws Exception {
        when(googleTokenVerifier.verify("forged-token")).thenReturn(Optional.empty());

        MvcResult result = googleLogin("forged-token");

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("login: a passwordless (Google-only) account cannot be logged into with any password")
    void login_googleOnlyAccount_rejectedWith401() throws Exception {
        String email = uniqueEmail();
        when(googleTokenVerifier.verify("google-token"))
                .thenReturn(Optional.of(new GoogleUser(UUID.randomUUID().toString(), email, true)));
        googleLogin("google-token");

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, "anything"))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
