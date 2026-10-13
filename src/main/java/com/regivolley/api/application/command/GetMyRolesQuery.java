package com.regivolley.api.application.command;

import java.util.Objects;

/** The caller asks which roles they hold now in their association. */
public record GetMyRolesQuery(Actor actor) {

    public GetMyRolesQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
