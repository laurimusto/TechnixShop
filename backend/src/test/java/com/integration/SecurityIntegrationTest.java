package com.integration;

import com.dto.user.LoginRequestDTO;
import com.dto.user.RegisterRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class SecurityIntegrationTest extends IntegrationTestBase {

    @Test
    @DisplayName("SQL injection in the login email is rejected as invalid credentials, not a 500 or an auth bypass")
    void sqlInjectionInLoginEmail_doesNotBypassAuthOrCrash() throws Exception {
        LoginRequestDTO payload = new LoginRequestDTO("' OR '1'='1' --", "anything");

        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();

        // either 401 (treated as an unknown/invalid login) or 400 (fails email format validation)
        // - the only unacceptable outcomes are 200 (auth bypass) or 500 (a crash/leak)
        assertThat(result.getResponse().getStatus()).isIn(401, 400);
    }

    @Test
    @DisplayName("SQL injection in the search name filter does not crash the endpoint")
    void sqlInjectionInSearchName_doesNotCrash() throws Exception {
        MvcResult result = mockMvc.perform(get("/search").param("name", "' OR '1'='1' --")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("malformed JSON body returns a clean 4xx error, never a raw 500 stack trace")
    void malformedJsonBody_returnsCleanErrorNotStackTrace() throws Exception {
        MvcResult result = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isLessThan(500);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("\tat ");
    }

    @Test
    @DisplayName("an XSS-style payload as an email is rejected by input validation, not stored or executed")
    void xssPayloadAsEmail_rejectedByValidation() throws Exception {
        RegisterRequestDTO payload = new RegisterRequestDTO("<script>alert(1)</script>", "password123", "password123", "test-captcha-token");

        MvcResult result = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("an XSS-style payload in the search name filter is treated as inert data, not executed, and doesn't crash the endpoint")
    void xssPayloadInSearchName_treatedAsInertData() throws Exception {
        MvcResult result = mockMvc.perform(get("/search").param("name", "<script>alert(1)</script>")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }
}
