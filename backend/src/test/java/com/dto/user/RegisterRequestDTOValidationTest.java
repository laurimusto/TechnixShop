package com.dto.user;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestDTOValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("a fully valid registration request has no violations")
    void validRequest_hasNoViolations() {
        RegisterRequestDTO request = new RegisterRequestDTO("test@example.com", "password", "password", "test-captcha-token");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("a blank email is rejected")
    void blankEmail_isRejected() {
        RegisterRequestDTO request = new RegisterRequestDTO("", "password", "password", "test-captcha-token");

        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("a malformed email is rejected")
    void malformedEmail_isRejected() {
        RegisterRequestDTO request = new RegisterRequestDTO("not-an-email", "password", "password", "test-captcha-token");

        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("a password shorter than 3 characters is rejected")
    void tooShortPassword_isRejected() {
        RegisterRequestDTO request = new RegisterRequestDTO("test@example.com", "ab", "ab", "test-captcha-token");

        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("a blank repeatPassword is rejected")
    void blankRepeatPassword_isRejected() {
        RegisterRequestDTO request = new RegisterRequestDTO("test@example.com", "password", "", "test-captcha-token");

        Set<ConstraintViolation<RegisterRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("repeatPassword"));
    }
}
