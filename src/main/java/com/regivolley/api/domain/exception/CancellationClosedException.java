package com.regivolley.api.domain.exception;

import java.time.Instant;

/** Thrown when a booking is cancelled at or after the session start (RN-10). */
public class CancellationClosedException extends BusinessRuleException {

    private final Instant startsAt;

    public CancellationClosedException(Instant startsAt) {
        super("The booking can no longer be cancelled: the session started at " + LisbonTimeFormat.format(startsAt));
        this.startsAt = startsAt;
    }

    public Instant startsAt() {
        return startsAt;
    }
}
