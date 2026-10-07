package com.regivolley.api.domain.exception;

/** An invariant of a training group does not hold (e.g. reconstructed with a negative version): a programming or data error, never shown to users. */
public class InvalidTrainingGroupException extends RuntimeException {

    public InvalidTrainingGroupException(String message) {
        super(message);
    }
}
