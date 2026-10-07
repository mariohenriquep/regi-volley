package com.regivolley.api.domain.exception;

import java.time.Instant;

/** Thrown when an operation that only makes sense before the session starts (waitlist promotion) runs too late. */
public class SessionAlreadyStartedException extends BusinessRuleException {

    private final Instant startsAt;

    public SessionAlreadyStartedException(Instant startsAt) {
        super("The session has already started (started at " + LisbonTimeFormat.format(startsAt) + ")");
        this.startsAt = startsAt;
    }

    public Instant startsAt() {
        return startsAt;
    }
}
