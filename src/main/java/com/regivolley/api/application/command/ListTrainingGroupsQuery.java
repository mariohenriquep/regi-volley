package com.regivolley.api.application.command;

import java.util.Objects;

/** An administrator reads the association's training groups (US-09); archived ones only when {@code includeArchived}. */
public record ListTrainingGroupsQuery(Actor actor, boolean includeArchived) {

    public ListTrainingGroupsQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
