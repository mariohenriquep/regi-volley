package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.LevelId;

import java.util.Objects;

/** An administrator chooses the level new members start at (US-03, RN-20). */
public record ChangeEntryLevelCommand(Actor actor, LevelId levelId) {

    public ChangeEntryLevelCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(levelId, "levelId must not be null");
    }
}
