package com.regivolley.api.application.command;

import java.util.Objects;

/** An administrator reads the association's levels (US-03). */
public record ListLevelsQuery(Actor actor) {

    public ListLevelsQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
