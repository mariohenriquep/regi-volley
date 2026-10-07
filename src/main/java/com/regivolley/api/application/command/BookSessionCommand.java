package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.SessionId;

import java.util.Objects;

/** A member asks for a place in a session (US-14); the member is the actor. */
public record BookSessionCommand(Actor actor, SessionId sessionId) {

    public BookSessionCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
}
