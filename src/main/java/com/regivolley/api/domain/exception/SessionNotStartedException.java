package com.regivolley.api.domain.exception;

import java.time.Instant;

/** Thrown when an operation that needs the session to have started (complete, attendance) runs too early. */
public class SessionNotStartedException extends BusinessRuleException {

    private final Instant startsAt;

    public SessionNotStartedException(Instant startsAt) {
        super("The session has not started yet (starts at " + LisbonTimeFormat.format(startsAt) + ")");
        this.startsAt = startsAt;
    }

    public Instant startsAt() {
        return startsAt;
    }
}
