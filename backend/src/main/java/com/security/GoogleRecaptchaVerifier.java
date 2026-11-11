package com.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class GoogleRecaptchaVerifier implements CaptchaVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleRecaptchaVerifier.class);
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final RestClient restClient;
    private final String secret;

    public GoogleRecaptchaVerifier(@Value("${recaptcha.secret}") String secret) {
        this.restClient = RestClient.create();
        this.secret = secret;
    }

    @Override
    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", token);

        try {
            RecaptchaResponse response = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(RecaptchaResponse.class);

            return response != null && response.success();
        } catch (Exception e) {
            log.error("reCAPTCHA verification call failed", e);
            return false;
        }
    }

    private record RecaptchaResponse(boolean success) {
    }
}
