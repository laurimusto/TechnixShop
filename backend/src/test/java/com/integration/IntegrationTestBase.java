package com.integration;

import com.security.CaptchaVerifier;
import com.security.GoogleTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Base for all @SpringBootTest integration tests: boots the real app against a real,
// throwaway Postgres container (Docker required) instead of mocks, so these exercise the
// real Spring Security filter chain, real Flyway migrations, and real DB persistence -
// exactly what the pure unit tests in AuthServiceTest etc. structurally cannot verify.
// MockMvc drives requests through the real DispatcherServlet without binding an OS port.
//
// The container is started once in a static initializer (not Testcontainers' per-class
// @Container lifecycle) and shared across every subclass, so the JVM only pays the
// container-startup cost once for the whole test run.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    // .env loading only happens in ILoveShopping1Application.main(), which @SpringBootTest
    // never calls - so every ${...} placeholder application.properties relies on is supplied
    // here directly instead, bypassing the env-var indirection entirely.
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("jwt.secret", () -> "integration-test-only-secret-key-that-is-long-enough-for-hs256");
        registry.add("jwt.expiration", () -> "1800000");
        registry.add("refresh.expiration", () -> "432000000");
        registry.add("reset.expiration", () -> "1800000");
        registry.add("app.frontend.url", () -> "http://localhost:3000");
        registry.add("cloudinary.cloud-name", () -> "test");
        registry.add("cloudinary.api-key", () -> "test");
        registry.add("cloudinary.api-secret", () -> "test");
        registry.add("recaptcha.secret", () -> "test");
        registry.add("google.client-id", () -> "test-client-id");
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;

    // replaces the real GoogleRecaptchaVerifier so integration tests never call Google
    @MockitoBean
    protected CaptchaVerifier captchaVerifier;

    // replaces the real GoogleIdTokenVerifierImpl so integration tests never call Google;
    // individual tests stub verify(...) with whatever Optional they need
    @MockitoBean
    protected GoogleTokenVerifier googleTokenVerifier;

    @BeforeEach
    void allowCaptchaByDefault() {
        when(captchaVerifier.verify(any())).thenReturn(true);
    }
}
