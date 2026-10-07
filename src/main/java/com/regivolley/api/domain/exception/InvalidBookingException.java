package com.regivolley.api.domain.exception;

/** Thrown when a {@code Booking} would be created in an invalid state (invariants). */
public class InvalidBookingException extends RuntimeException {

    public InvalidBookingException(String message) {
        super(message);
    }
}
