package com.regivolley.api.domain.exception;

/** Thrown when a {@code Subscription} would be created in an invalid state (invariants). */
public class InvalidSubscriptionException extends RuntimeException {

    public InvalidSubscriptionException(String message) {
        super(message);
    }
}
