package com.regivolley.api.application.result;

import java.time.Instant;
import java.util.Objects;

/**
 * What a login or a refresh hands out: the short-lived access token with its lifetime in seconds, and the opaque refresh token
 * with the instant it stops being usable. Both token values are credentials: never log them.
 */
public record SessionTokens(String accessToken, long expiresInSeconds, String refreshToken, Instant refreshExpiresAt) {

    public SessionTokens {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(refreshToken, "refreshToken must not be null");
        Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt must not be null");
    }

    @Override
    public String toString() {
        return "SessionTokens{expiresInSeconds=" + expiresInSeconds + ", refreshExpiresAt=" + refreshExpiresAt + "}";
    }
}
