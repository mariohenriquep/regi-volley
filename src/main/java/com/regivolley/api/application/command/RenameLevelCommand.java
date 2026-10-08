package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.LevelId;

import java.util.Objects;

/** An administrator renames a level (US-03). */
public record RenameLevelCommand(Actor actor, LevelId levelId, String newName) {

    public RenameLevelCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(levelId, "levelId must not be null");
        Objects.requireNonNull(newName, "newName must not be null");
    }
}
