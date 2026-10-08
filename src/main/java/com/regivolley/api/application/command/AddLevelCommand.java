package com.regivolley.api.application.command;

import java.util.Objects;

/** An administrator adds a level as the most advanced one (US-03). */
public record AddLevelCommand(Actor actor, String name) {

    public AddLevelCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
