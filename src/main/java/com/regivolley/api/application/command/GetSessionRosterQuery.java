package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.SessionId;

import java.util.Objects;

/** The staff of a session (its coach or an administrator) ask who holds a seat or a waitlist place in it (US-17). */
public record GetSessionRosterQuery(Actor actor, SessionId sessionId) {

    public GetSessionRosterQuery {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
    }
}
