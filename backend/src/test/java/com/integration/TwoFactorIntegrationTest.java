package com.integration;

import com.dto.user.AuthResponseDTO;
import com.dto.user.GoogleLoginRequestDTO;
import com.dto.user.LoginRequestDTO;
import com.dto.user.MeResponseDTO;
import com.dto.user.RegisterRequestDTO;
import com.dto.user.TwoFactorChallengeDTO;
import com.dto.user.TwoFactorCodeRequestDTO;
import com.dto.user.TwoFactorLoginRequestDTO;
import com.dto.user.TwoFactorSetupResponseDTO;
import com.repository.UserRepository;
import com.security.GoogleTokenVerifier.GoogleUser;
import com.security.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// The whole 2FA flow through the real HTTP layer and database.
// The Clock is mocked: TOTP codes depend on the time, and a real clock could cross a 30-second
// boundary in the middle of a test and make it fail at random.
class TwoFactorIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "password123";
    // 15 s into a 30-second step, so "now" is far from both edges
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:15Z");

    @MockitoBean
    private Clock clock;

    @Autowired
    private UserRepository userRepository;

    private final TotpService totp = new TotpService();

    @BeforeEach
    void fixTime() {
        setTime(NOW);
    }

    private void setTime(Instant time) {
        when(clock.instant()).thenReturn(time);
    }

    private String uniqueEmail() {
        return "t" + UUID.randomUUID().toString().substring(0, 8) + "@ex.com";
    }

    // --- helpers: each returns what the next step needs ---

    private String register(String email) throws Exception {
        MvcResult result = postJson("/register", null,
                new RegisterRequestDTO(email, PASSWORD, PASSWORD, "test-captcha-token", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return read(result, AuthResponseDTO.class).token();
    }

    private byte[] setup(String token) throws Exception {
        MvcResult result = postJson("/me/2fa/setup", token, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        TwoFactorSetupResponseDTO body = read(result, TwoFactorSetupResponseDTO.class);
        assertThat(body.otpauthUri()).startsWith("otpauth://totp/").contains("secret=" + body.secret());
        return TotpService.fromBase32(body.secret()); // what the phone does after scanning the QR code
    }

    private String codeAt(byte[] secret, Instant time) {
        return totp.generateCode(secret, TotpService.timeStep(time));
    }

    // a code that matches none of the 3 accepted steps around `time`
    private String wrongCode(byte[] secret, Instant time) {
        long step = TotpService.timeStep(time);
        Set<String> valid = Set.of(totp.generateCode(secret, step - 1), totp.generateCode(secret, step),
                totp.generateCode(secret, step + 1));
        for (int i = 0; ; i++) {
            String candidate = String.format("%06d", i);
            if (!valid.contains(candidate)) return candidate;
        }
    }

    private int enable(String token, String code) throws Exception {
        return postJson("/me/2fa/enable", token, new TwoFactorCodeRequestDTO(code)).getResponse().getStatus();
    }

    private int disable(String token, String code) throws Exception {
        return postJson("/me/2fa/disable", token, new TwoFactorCodeRequestDTO(code)).getResponse().getStatus();
    }

    // register + setup + enable: an account with 2FA on. Returns the phone's secret.
    private byte[] accountWithTwoFactor(String email) throws Exception {
        String token = register(email);
        byte[] secret = setup(token);
        assertThat(enable(token, codeAt(secret, NOW))).isEqualTo(204);
        return secret;
    }

    private MvcResult login(String email) throws Exception {
        return postJson("/login", null, new LoginRequestDTO(email, PASSWORD));
    }

    private String challengeFrom(MvcResult loginResult) throws Exception {
        assertThat(loginResult.getResponse().getStatus()).isEqualTo(200);
        TwoFactorChallengeDTO body = read(loginResult, TwoFactorChallengeDTO.class);
        assertThat(body.twoFactorRequired()).isTrue();
        return body.challengeToken();
    }

    private MvcResult loginTwoFactor(String challenge, String code) throws Exception {
        return postJson("/login/2fa", null, new TwoFactorLoginRequestDTO(challenge, code));
    }

    private boolean twoFactorEnabled(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/me").header("Authorization", "Bearer " + token)).andReturn();
        return read(result, MeResponseDTO.class).twoFactorEnabled();
    }

    private MvcResult postJson(String path, String token, Object body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(objectMapper.writeValueAsString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request).andReturn();
    }

    private <T> T read(MvcResult result, Class<T> type) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), type);
    }

    // --- tests ---

    @Test
    @DisplayName("the 2FA endpoints require a logged-in user")
    void endpointsRequireAuth() throws Exception {
        assertThat(postJson("/me/2fa/setup", null, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(enable(null, "123456")).isEqualTo(401);
        assertThat(disable(null, "123456")).isEqualTo(401);
    }

    @Test
    @DisplayName("setup alone doesn't turn 2FA on; a wrong code doesn't either; the right code does")
    void enableFlow() throws Exception {
        String token = register(uniqueEmail());
        byte[] secret = setup(token);
        assertThat(twoFactorEnabled(token)).isFalse();

        assertThat(enable(token, wrongCode(secret, NOW))).isEqualTo(400);
        assertThat(enable(token, "12ab56")).isEqualTo(400); // validation
        assertThat(twoFactorEnabled(token)).isFalse();

        assertThat(enable(token, codeAt(secret, NOW))).isEqualTo(204);
        assertThat(twoFactorEnabled(token)).isTrue();

        // a second setup would silently replace the secret on the phone: refused
        assertThat(postJson("/me/2fa/setup", token, null).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("the secret is stored encrypted, not as the Base32 text the user saw")
    void secretStoredEncrypted() throws Exception {
        String email = uniqueEmail();
        byte[] secret = accountWithTwoFactor(email);

        String stored = userRepository.findByEmail(email).orElseThrow().getTotpSecret();
        assertThat(stored).isNotBlank().doesNotContain(TotpService.toBase32(secret));
    }

    @Test
    @DisplayName("with 2FA on, the password alone gives a challenge but no token and no refresh cookie")
    void passwordAloneIsNotEnough() throws Exception {
        String email = uniqueEmail();
        accountWithTwoFactor(email);

        MvcResult result = login(email);

        assertThat(challengeFrom(result)).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"token\"");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("second step: wrong code 400, replayed code 400, fresh code signs in, challenge is single-use")
    void secondStep() throws Exception {
        String email = uniqueEmail();
        byte[] secret = accountWithTwoFactor(email); // used the code of step NOW
        String challenge = challengeFrom(login(email));

        assertThat(loginTwoFactor(challenge, wrongCode(secret, NOW)).getResponse().getStatus()).isEqualTo(400);
        // the enable code is still inside the time window, but was already used once
        assertThat(loginTwoFactor(challenge, codeAt(secret, NOW)).getResponse().getStatus()).isEqualTo(400);

        Instant later = NOW.plusSeconds(30);
        setTime(later);
        MvcResult ok = loginTwoFactor(challenge, codeAt(secret, later));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        String accessToken = read(ok, AuthResponseDTO.class).token();
        assertThat(accessToken).isNotBlank();
        assertThat(ok.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith("refreshToken=");

        // the new session works
        assertThat(mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        // challenge is used up, even with the next valid code
        Instant evenLater = later.plusSeconds(30);
        setTime(evenLater);
        assertThat(loginTwoFactor(challenge, codeAt(secret, evenLater)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("an unknown or expired challenge is 401: the login has to start over")
    void unknownChallenge() throws Exception {
        assertThat(loginTwoFactor("made-up-challenge", "123456").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("the challenge can't be used as an access token")
    void challengeIsNotAnAccessToken() throws Exception {
        String email = uniqueEmail();
        accountWithTwoFactor(email);
        String challenge = challengeFrom(login(email));

        assertThat(mockMvc.perform(get("/me").header("Authorization", "Bearer " + challenge))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("after 5 wrong codes even the correct one is refused with 429")
    void bruteForceLockout() throws Exception {
        String token = register(uniqueEmail());
        byte[] secret = setup(token);

        for (int i = 0; i < 5; i++) {
            assertThat(enable(token, wrongCode(secret, NOW))).isEqualTo(400);
        }
        assertThat(enable(token, codeAt(secret, NOW))).isEqualTo(429);
        assertThat(twoFactorEnabled(token)).isFalse();
    }

    @Test
    @DisplayName("Google sign-in also asks for the code when 2FA is on")
    void googleLoginNeedsCodeToo() throws Exception {
        String email = uniqueEmail();
        String sub = UUID.randomUUID().toString();
        when(googleTokenVerifier.verify("g-token")).thenReturn(Optional.of(new GoogleUser(sub, email, true)));

        MvcResult first = postJson("/googlelogin", null, new GoogleLoginRequestDTO("g-token"));
        String token = read(first, AuthResponseDTO.class).token();
        byte[] secret = setup(token);
        assertThat(enable(token, codeAt(secret, NOW))).isEqualTo(204);

        MvcResult second = postJson("/googlelogin", null, new GoogleLoginRequestDTO("g-token"));
        assertThat(challengeFrom(second)).isNotBlank();
        assertThat(second.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    @DisplayName("disabling needs a valid code; afterwards the password alone signs in again")
    void disableFlow() throws Exception {
        String email = uniqueEmail();
        String token = register(email);
        byte[] secret = setup(token);
        assertThat(enable(token, codeAt(secret, NOW))).isEqualTo(204);

        assertThat(disable(token, wrongCode(secret, NOW))).isEqualTo(400);
        assertThat(twoFactorEnabled(token)).isTrue();

        Instant later = NOW.plusSeconds(30);
        setTime(later);
        assertThat(disable(token, codeAt(secret, later))).isEqualTo(204);
        assertThat(twoFactorEnabled(token)).isFalse();
        assertThat(userRepository.findByEmail(email).orElseThrow().getTotpSecret()).isNull();

        MvcResult result = login(email);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(result, AuthResponseDTO.class).token()).isNotBlank();

        // already off: nothing to disable
        assertThat(disable(token, codeAt(secret, later))).isEqualTo(400);
    }
}
