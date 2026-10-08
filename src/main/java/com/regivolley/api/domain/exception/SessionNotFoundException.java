package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.SessionId;

/** Thrown when a session id doesn't exist in the association it is addressed to (maps to "not found"). */
public class SessionNotFoundException extends RuntimeException {

    private final SessionId sessionId;

    public SessionNotFoundException(SessionId sessionId) {
        super("Session not found in this association: " + sessionId);
        this.sessionId = sessionId;
    }

    public SessionId sessionId() {
        return sessionId;
    }
}
