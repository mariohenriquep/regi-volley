package com.regivolley.api.domain.exception;

/** Thrown when a {@code Association} would be created or changed into an invalid state (invariants). */
public class InvalidAssociationException extends RuntimeException {

    public InvalidAssociationException(String message) {
        super(message);
    }
}
