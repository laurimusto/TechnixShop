package com.exception;

public class NoPermissionsException extends RuntimeException {
    public NoPermissionsException(String message) {
        super(message);
    }
}
