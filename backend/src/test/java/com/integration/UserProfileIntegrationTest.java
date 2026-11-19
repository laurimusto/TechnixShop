package com.integration;

import com.dto.user.AuthResponseDTO;
import com.dto.user.MeResponseDTO;
import com.dto.user.ProfileDTO;
import com.dto.user.RegisterRequestDTO;
import com.entity.User;
import com.enums.Role;
import com.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// GET/PUT /me: the logged-in user's own profile.
class UserProfileIntegrationTest extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    private static final ProfileDTO FULL_PROFILE =
            new ProfileDTO("Mari", "Tamm", "+372 5123 4567", "Narva mnt 5", "10117", "Tallinn", "Estonia");

    private String uniqueEmail() {
        return "p" + UUID.randomUUID().toString().substring(0, 8) + "@ex.com";
    }

    // registers and returns the access token
    private String register(String email, ProfileDTO profile) throws Exception {
        MvcResult result = mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequestDTO(email, "password123", "password123", "test-captcha-token", profile))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class).token();
    }

    private MvcResult getMe(String token) throws Exception {
        return mockMvc.perform(get("/me").header("Authorization", "Bearer " + token)).andReturn();
    }

    private MvcResult putMe(String token, String json) throws Exception {
        return mockMvc.perform(put("/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
    }

    private MeResponseDTO read(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), MeResponseDTO.class);
    }

    @Test
    @DisplayName("GET and PUT /me without a token are rejected with 401")
    void me_withoutToken_returns401() throws Exception {
        assertThat(mockMvc.perform(get("/me")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(put("/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("register without profile: GET /me returns empty profile, not ready for ordering")
    void registerWithoutProfile_meIsEmpty() throws Exception {
        String email = uniqueEmail();
        MeResponseDTO me = read(getMe(register(email, null)));

        assertThat(me.email()).isEqualTo(email);
        assertThat(me.profile().firstName()).isNull();
        assertThat(me.hasPassword()).isTrue();
        assertThat(me.readyForOrdering()).isFalse();
    }

    @Test
    @DisplayName("register with a full profile: it is saved and the user is ready for ordering")
    void registerWithProfile_isSaved() throws Exception {
        MeResponseDTO me = read(getMe(register(uniqueEmail(), FULL_PROFILE)));

        assertThat(me.profile()).isEqualTo(FULL_PROFILE);
        assertThat(me.readyForOrdering()).isTrue();
    }

    @Test
    @DisplayName("PUT /me updates the profile, trims values and stores empty strings as null")
    void putMe_updatesAndCleans() throws Exception {
        String token = register(uniqueEmail(), FULL_PROFILE);

        MvcResult result = putMe(token, """
                {"firstName":"  Jaan  ","lastName":"Kask","phone":"","street":"","postalCode":"","city":"","country":""}
                """);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        MeResponseDTO me = read(getMe(token));
        assertThat(me.profile().firstName()).isEqualTo("Jaan");
        assertThat(me.profile().phone()).isNull();
        assertThat(me.readyForOrdering()).isFalse();
    }

    @Test
    @DisplayName("PUT /me with an invalid phone is rejected with 400 and a readable message")
    void putMe_invalidPhone_returns400() throws Exception {
        String token = register(uniqueEmail(), null);

        MvcResult result = putMe(token, """
                {"phone":"call me maybe"}
                """);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("Phone number");
    }

    @Test
    @DisplayName("mass assignment: role and email sent to PUT /me are ignored")
    void putMe_cannotChangeRoleOrEmail() throws Exception {
        String email = uniqueEmail();
        String token = register(email, null);

        putMe(token, """
                {"firstName":"Eve","role":"ADMIN","email":"attacker@ex.com"}
                """);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getFirstName()).isEqualTo("Eve");
        assertThat(userRepository.findByEmail("attacker@ex.com")).isEmpty();
    }
}
