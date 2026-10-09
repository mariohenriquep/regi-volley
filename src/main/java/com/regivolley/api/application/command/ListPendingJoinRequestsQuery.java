package com.regivolley.api.application.command;

import java.util.Objects;

/** The administrator asks for the join requests that wait for a decision (US-06). */
public record ListPendingJoinRequestsQuery(Actor actor) {

    public ListPendingJoinRequestsQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
