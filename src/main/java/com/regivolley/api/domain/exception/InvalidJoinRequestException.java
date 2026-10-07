package com.regivolley.api.domain.exception;

/** Thrown when a {@code JoinRequest} would be created or changed into an invalid state (invariants). */
public class InvalidJoinRequestException extends RuntimeException {

    public InvalidJoinRequestException(String message) {
        super(message);
    }
}
