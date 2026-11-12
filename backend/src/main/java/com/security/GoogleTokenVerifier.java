package com.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class GoogleTokenVerifier {

    public record GoogleUser(String sub, String email, boolean emailVerified) {
    }

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    // Google issues ID tokens with either form of its issuer
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder jwtDecoder;

    public GoogleTokenVerifier(@Value("${google.client-id}") String googleClientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<Object>(JwtClaimNames.ISS,
                        iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString())),
                // without this, a Google token issued to any other app would be accepted here
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(googleClientId))
        ));
        this.jwtDecoder = decoder;
    }

    public Optional<GoogleUser> verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            boolean emailVerified = Boolean.TRUE.equals(jwt.getClaim("email_verified"));
            return Optional.of(new GoogleUser(jwt.getSubject(), jwt.getClaim("email"), emailVerified));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
