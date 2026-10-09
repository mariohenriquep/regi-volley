package com.regivolley.api.application.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An emailed link as the mail adapter needs it: {@code reference} identifies the stored link (for logs and support) and
 * {@code token} is the secret that goes into the URL. The token exists in clear only here and in the mail - the database
 * keeps a hash - so it is a credential: {@link #toString()} prints the reference, never the token, and nothing may log it.
 */
public record AccountLink(UUID reference, String token, Instant expiresAt) {

    public AccountLink {
        Objects.requireNonNull(reference, "reference must not be null");
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
    }

    @Override
    public String toString() {
        return "AccountLink{reference=" + reference + ", expiresAt=" + expiresAt + "}";
    }
}
