package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.SessionId;

import java.util.Objects;

/** Cancels a whole session with a reason (US-11, RN-04). The actor must coach the session or be an administrator. */
public record CancelSessionCommand(Actor actor, SessionId sessionId, String reason) {

    public CancelSessionCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
}
