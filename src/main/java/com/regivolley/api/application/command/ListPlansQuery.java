package com.regivolley.api.application.command;

import java.util.Objects;

/** An administrator reads the association's plans (US-19). */
public record ListPlansQuery(Actor actor) {

    public ListPlansQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
