package com.regivolley.api.domain.exception;

/**
 * Thrown when a recurring schedule (or one of its weekly slots) is malformed: no slots, a
 * duration out of bounds, or two slots of the same group overlapping (US-09).
 */
public class InvalidScheduleException extends BusinessRuleException {

    public InvalidScheduleException(String message) {
        super(message);
    }
}
