package com.regivolley.api.domain.exception;

/** Thrown when a {@code Session} would be created or changed into an invalid state (invariants). */
public class InvalidSessionException extends RuntimeException {

    public InvalidSessionException(String message) {
        super(message);
    }
}
