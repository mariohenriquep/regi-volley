package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.SessionStatus;

/** Thrown when a {@code Session} is asked to move to a status not reachable from its current one (RN-05). */
public class InvalidSessionStatusTransitionException extends BusinessRuleException {

    private final SessionStatus from;
    private final SessionStatus to;

    public InvalidSessionStatusTransitionException(SessionStatus from, SessionStatus to) {
        super("Cannot move the session from " + label(from) + " to " + label(to));
        this.from = from;
        this.to = to;
    }

    private static String label(SessionStatus status) {
        return switch (status) {
            case SCHEDULED -> "scheduled";
            case COMPLETED -> "completed";
            case CANCELLED -> "cancelled";
        };
    }

    public SessionStatus from() {
        return from;
    }

    public SessionStatus to() {
        return to;
    }
}
