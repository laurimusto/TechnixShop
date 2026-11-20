package com.repository;

import java.time.Instant;

// What JwtAuthenticationFilter needs to know about a token's user on every request.
// Wrapping the column in a record separates the two "nothing" cases a bare Optional<Instant> mixed up:
//   Optional.empty()               -> the user row is gone (account deleted)
//   present, passwordChangedAt null -> the user exists and never changed their password
public record UserSessionState(Instant passwordChangedAt) {
}
