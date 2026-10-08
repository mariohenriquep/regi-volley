package com.regivolley.api.application.exception;

import java.time.Duration;

/** A rate limit was hit; the client is told how long to wait (HTTP 429 with {@code Retry-After}). Carries no key and no address. */
public class RateLimitExceededException extends RuntimeException {

    private final transient Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many requests");
        this.retryAfter = retryAfter;
    }

    /** Whole seconds to wait, at least one. */
    public long retryAfterSeconds() {
        long seconds = (retryAfter.toMillis() + 999) / 1000;
        return Math.max(1, seconds);
    }
}
