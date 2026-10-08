package com.regivolley.api.domain.exception;

/** An invariant of a venue does not hold (e.g. reconstructed with a negative version): a programming or data error, never shown to users. */
public class InvalidVenueException extends RuntimeException {

    public InvalidVenueException(String message) {
        super(message);
    }
}
