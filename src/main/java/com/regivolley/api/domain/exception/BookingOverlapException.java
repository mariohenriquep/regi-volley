package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.SessionId;

import java.time.Instant;

/** Thrown when the member already holds a non-cancelled booking in another session at the same time (RN-07). */
public class BookingOverlapException extends BusinessRuleException {

    private final SessionId overlappingSessionId;

    public BookingOverlapException(SessionId overlappingSessionId, Instant overlappingStartsAt) {
        super("The member already has a booking in another session at the same time (starting "
                + LisbonTimeFormat.format(overlappingStartsAt) + ")");
        this.overlappingSessionId = overlappingSessionId;
    }

    public SessionId overlappingSessionId() {
        return overlappingSessionId;
    }
}
