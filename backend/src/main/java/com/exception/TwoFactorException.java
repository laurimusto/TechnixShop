package com.exception;

// Every "no" from 2FA that the user can fix: a wrong or reused code, or the wrong state
// (enable when it's already on, disable when it's off). All of them are a 400 with a readable message.
public class TwoFactorException extends RuntimeException {
    public TwoFactorException(String message) {
        super(message);
    }
}
