package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.SessionId;

/**
 * A session (and so its bookings, capacity and waitlist) was changed by a concurrent request
 * between load and save, so this write was rejected and nothing was stored. This is how the last
 * seat is protected (architecture.md section 10): the booking use case catches it, reloads the
 * session and tries again, which then confirms, waitlists or rejects the member against the
 * up-to-date state. Also raised when the same occurrence of a training group is generated twice at
 * once, and when a session saved with a version has no stored row in the association.
 */
public class SessionModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final SessionId sessionId;

    public SessionModifiedConcurrentlyException(SessionId sessionId) {
        super("session", sessionId.value());
        this.sessionId = sessionId;
    }

    public SessionId sessionId() {
        return sessionId;
    }
}
