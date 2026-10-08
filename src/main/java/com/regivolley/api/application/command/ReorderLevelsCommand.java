package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.LevelId;

import java.util.List;
import java.util.Objects;

/** An administrator sets the order of all the levels, most basic first (US-03). */
public record ReorderLevelsCommand(Actor actor, List<LevelId> orderedLevelIds) {

    public ReorderLevelsCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(orderedLevelIds, "orderedLevelIds must not be null");
    }
}
