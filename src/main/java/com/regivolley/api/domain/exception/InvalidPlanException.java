package com.regivolley.api.domain.exception;

/** Thrown when a {@code Plan} (or its terms) would be created in an invalid state (invariants). */
public class InvalidPlanException extends RuntimeException {

    public InvalidPlanException(String message) {
        super(message);
    }
}
