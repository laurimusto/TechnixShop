package com.exception;

import com.dto.error.ErrorResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("ResourceNotFoundException maps to 404 with its own message")
    void handlesResourceNotFound() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleNotFound(new ResourceNotFoundException("not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("not found");
    }

    @Test
    @DisplayName("EmailAlreadyExistsException maps to 409 Conflict")
    void handlesEmailAlreadyExists() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleEmailAlreadyExists(new EmailAlreadyExistsException("taken"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("taken");
    }

    @Test
    @DisplayName("PasswordMismatchException maps to 400 Bad Request")
    void handlesPasswordMismatch() {
        ResponseEntity<ErrorResponseDTO> response = handler.handlePasswordMismatch(new PasswordMismatchException("no match"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PasswordTooShortException maps to 400 Bad Request")
    void handlesPasswordTooShort() {
        ResponseEntity<ErrorResponseDTO> response = handler.handlePasswordTooShort(new PasswordTooShortException("too short"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("InvalidEmailException maps to 400 Bad Request")
    void handlesInvalidEmail() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleInvalidEmail(new InvalidEmailException("bad email"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("InvalidCredentialsException maps to 401 Unauthorized")
    void handlesInvalidCredentials() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleInvalidCredentials(new InvalidCredentialsException("bad login"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("InvalidRefreshTokenException maps to 401 Unauthorized")
    void handlesInvalidRefreshToken() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleInvalidRefreshToken(new InvalidRefreshTokenException("bad token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("PasswordReuseException maps to 400 Bad Request")
    void handlesPasswordReuse() {
        ResponseEntity<ErrorResponseDTO> response = handler.handlePasswordInUse(new PasswordReuseException("already used"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("InvalidPasswordException maps to 400 Bad Request")
    void handlesInvalidPassword() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleInvalidOldPassword(new InvalidPasswordException("wrong current password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("NoPermissionsException maps to 404 Not Found (deliberately hides existence rather than 403)")
    void handlesNoPermissions() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleAccessDenied(new NoPermissionsException("no access"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("UploadFailedException maps to 400 Bad Request")
    void handlesUploadFailed() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleUploadFailed(new UploadFailedException("upload failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException maps to 413 Payload Too Large")
    void handlesMaxUploadSize() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleMaxSize(new MaxUploadSizeExceededException(10_000_000L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("MethodArgumentNotValidException returns the first field error's message")
    void handlesValidation_returnsFirstFieldErrorMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "email", "Invalid email format");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponseDTO> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Invalid email format");
    }

    @Test
    @DisplayName("an unexpected exception maps to 500 with a generic message, never leaking the real exception message")
    void handlesUnexpectedException_hidesRealMessage() {
        ResponseEntity<ErrorResponseDTO> response = handler.handleAll(new RuntimeException("leaked db password: hunter2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).doesNotContain("hunter2");
    }
}
