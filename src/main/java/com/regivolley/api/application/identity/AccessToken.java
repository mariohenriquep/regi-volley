package com.regivolley.api.application.identity;

import java.time.Instant;
import java.util.Objects;

/** A freshly issued access token and the instant it stops being valid. The value is a credential: never log it. */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    @Override
    public String toString() {
        return "AccessToken{expiresAt=" + expiresAt + "}";
    }
}
