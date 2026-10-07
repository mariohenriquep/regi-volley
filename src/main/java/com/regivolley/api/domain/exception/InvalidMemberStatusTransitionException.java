package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.MemberStatus;

/** Thrown when a {@code Member} is asked to move to a status not reachable from its current one (US-08). */
public class InvalidMemberStatusTransitionException extends BusinessRuleException {

    private final MemberStatus from;
    private final MemberStatus to;

    public InvalidMemberStatusTransitionException(MemberStatus from, MemberStatus to) {
        super("Cannot move the member from " + label(from) + " to " + label(to));
        this.from = from;
        this.to = to;
    }

    private static String label(MemberStatus status) {
        return switch (status) {
            case ACTIVE -> "active";
            case INACTIVE -> "inactive";
        };
    }

    public MemberStatus from() {
        return from;
    }

    public MemberStatus to() {
        return to;
    }
}
