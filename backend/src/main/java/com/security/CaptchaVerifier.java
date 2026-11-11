package com.security;

public interface CaptchaVerifier {
    boolean verify(String token);
}
