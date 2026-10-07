package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.SessionStatus;

/** Thrown when an operation needs a SCHEDULED session (book, cancel a booking, change capacity, promote). */
public class SessionNotScheduledException extends BusinessRuleException {

    private final SessionStatus status;

    public SessionNotScheduledException(SessionStatus status) {
        super("The session is no longer scheduled (status: " + label(status) + ")");
        this.status = status;
    }

    private static String label(SessionStatus status) {
        return switch (status) {
            case SCHEDULED -> "scheduled";
            case COMPLETED -> "completed";
            case CANCELLED -> "cancelled";
        };
    }

    public SessionStatus status() {
        return status;
    }
}
