package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.SessionId;

import java.util.Objects;

/** Changes the capacity of one session (US-12, RN-02). The actor must coach the session or be an administrator. */
public record ChangeSessionCapacityCommand(Actor actor, SessionId sessionId, int capacity) {

    public ChangeSessionCapacityCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
}
