package com.regivolley.api.domain.exception;

/** Thrown when a venue is given fewer than one court (US-02). */
public class InvalidCourtCountException extends BusinessRuleException {

    private final int requestedCourts;

    public InvalidCourtCountException(int requestedCourts) {
        super("A venue needs at least one court (requested: " + requestedCourts + ")");
        this.requestedCourts = requestedCourts;
    }

    public int requestedCourts() {
        return requestedCourts;
    }
}
