package com.regivolley.api.application.command;

import java.util.Objects;

/** An administrator reads the association's venues (US-02). */
public record ListVenuesQuery(Actor actor) {

    public ListVenuesQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
