package com.exception;

public class InvalidEmailChangeTokenException extends RuntimeException {
    public InvalidEmailChangeTokenException(String message) {
        super(message);
    }
}
