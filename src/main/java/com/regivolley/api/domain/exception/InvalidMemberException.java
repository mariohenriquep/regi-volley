package com.regivolley.api.domain.exception;

/** Thrown when a {@code Member} would be created or changed into an invalid state (invariants). */
public class InvalidMemberException extends RuntimeException {

    public InvalidMemberException(String message) {
        super(message);
    }
}
