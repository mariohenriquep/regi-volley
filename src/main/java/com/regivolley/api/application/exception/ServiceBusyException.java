package com.regivolley.api.application.exception;

/**
 * The service cannot take the request right now (every password-hashing slot stayed busy past the wait). The client is told to
 * retry after a short while (HTTP 503 with {@code Retry-After}); nothing was done.
 */
public class ServiceBusyException extends RuntimeException {

    private final long retryAfterSeconds;

    public ServiceBusyException(long retryAfterSeconds) {
        super("The service is busy");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
