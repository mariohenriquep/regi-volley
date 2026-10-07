package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.JoinRequestStatus;

/** Thrown when a {@code JoinRequest} is asked to move to a status not reachable from its current one (US-06). */
public class InvalidJoinRequestStatusTransitionException extends BusinessRuleException {

    private final JoinRequestStatus from;
    private final JoinRequestStatus to;

    public InvalidJoinRequestStatusTransitionException(JoinRequestStatus from, JoinRequestStatus to) {
        super("Cannot move the join request from " + label(from) + " to " + label(to));
        this.from = from;
        this.to = to;
    }

    private static String label(JoinRequestStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case APPROVED -> "approved";
            case REJECTED -> "rejected";
        };
    }

    public JoinRequestStatus from() {
        return from;
    }

    public JoinRequestStatus to() {
        return to;
    }
}
