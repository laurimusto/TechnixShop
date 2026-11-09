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

class LoginRequestDTOValidationTest {

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
    @DisplayName("a fully valid login request has no violations")
    void validRequest_hasNoViolations() {
        LoginRequestDTO request = new LoginRequestDTO("test@example.com", "password");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("a blank email is rejected")
    void blankEmail_isRejected() {
        LoginRequestDTO request = new LoginRequestDTO("", "password");

        Set<ConstraintViolation<LoginRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("a malformed email is rejected")
    void malformedEmail_isRejected() {
        LoginRequestDTO request = new LoginRequestDTO("not-an-email", "password");

        Set<ConstraintViolation<LoginRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("a blank password is rejected")
    void blankPassword_isRejected() {
        LoginRequestDTO request = new LoginRequestDTO("test@example.com", "");

        Set<ConstraintViolation<LoginRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("a password shorter than 3 characters is rejected")
    void tooShortPassword_isRejected() {
        LoginRequestDTO request = new LoginRequestDTO("test@example.com", "ab");

        Set<ConstraintViolation<LoginRequestDTO>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
