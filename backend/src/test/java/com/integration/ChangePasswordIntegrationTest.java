package com.integration;

import com.dto.user.AuthResponseDTO;
import com.dto.user.ChangePasswordRequestDTO;
import com.dto.user.LoginRequestDTO;
import com.dto.user.RegisterRequestDTO;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// POST /me/password: a logged-in user changes their password (re-authenticating with the current one).
class ChangePasswordIntegrationTest extends IntegrationTestBase {

    private static final String OLD_PASSWORD = "password123";
    private static final String NEW_PASSWORD = "newPassword456";

    private String uniqueEmail() {
        return "c" + UUID.randomUUID().toString().substring(0, 8) + "@ex.com";
    }

    private MvcResult register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequestDTO(email, OLD_PASSWORD, OLD_PASSWORD, "test-captcha-token", null))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return result;
    }

    private String tokenOf(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class).token();
    }

    private MvcResult changePassword(String token, String current, String newPassword, String repeat) throws Exception {
        return mockMvc.perform(post("/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangePasswordRequestDTO(current, newPassword, repeat))))
                .andReturn();
    }

    private int login(String email, String password) throws Exception {
        return mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO(email, password))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("without a token the request is rejected with 401")
    void withoutToken_returns401() throws Exception {
        int status = mockMvc.perform(post("/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequestDTO(OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(401);
    }

    @Test
    @DisplayName("wrong current password: 400 and the old password still works")
    void wrongCurrentPassword_returns400() throws Exception {
        String email = uniqueEmail();
        String token = tokenOf(register(email));

        MvcResult result = changePassword(token, "notMyPassword", NEW_PASSWORD, NEW_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("Current password is incorrect");
        assertThat(login(email, OLD_PASSWORD)).isEqualTo(200);
    }

    @Test
    @DisplayName("new and repeat password differ: 400")
    void mismatch_returns400() throws Exception {
        String token = tokenOf(register(uniqueEmail()));

        MvcResult result = changePassword(token, OLD_PASSWORD, NEW_PASSWORD, "somethingElse");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("do not match");
    }

    @Test
    @DisplayName("new password same as current: 400")
    void samePassword_returns400() throws Exception {
        String token = tokenOf(register(uniqueEmail()));

        MvcResult result = changePassword(token, OLD_PASSWORD, OLD_PASSWORD, OLD_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("different");
    }

    @Test
    @DisplayName("blank fields fail validation with 400")
    void blankFields_returns400() throws Exception {
        String token = tokenOf(register(uniqueEmail()));

        assertThat(changePassword(token, "", "", "").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("success: 204, only the new password logs in, old access and refresh tokens are rejected")
    void success_changesPasswordAndEndsSessions() throws Exception {
        String email = uniqueEmail();
        MvcResult registered = register(email);
        String oldToken = tokenOf(registered);
        Cookie oldRefreshCookie = registered.getResponse().getCookie("refreshToken");

        // iat and passwordChangedAt have whole-second precision; same-second tokens are allowed, so wait
        Thread.sleep(1100);

        assertThat(changePassword(oldToken, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD).getResponse().getStatus())
                .isEqualTo(204);

        assertThat(login(email, OLD_PASSWORD)).isEqualTo(401);
        assertThat(login(email, NEW_PASSWORD)).isEqualTo(200);
        assertThat(mockMvc.perform(get("/me").header("Authorization", "Bearer " + oldToken))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post("/refresh").cookie(oldRefreshCookie))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
