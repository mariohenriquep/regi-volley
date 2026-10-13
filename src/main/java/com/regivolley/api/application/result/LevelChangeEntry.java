package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.LevelChange;

import java.util.Objects;
import java.util.Optional;

/** One move in a member's level history with the names an administrator reads (each empty if it cannot be found). */
public record LevelChangeEntry(LevelChange change, Optional<String> fromName, Optional<String> toName, Optional<String> changedByName) {

    public LevelChangeEntry {
        Objects.requireNonNull(change, "change must not be null");
        Objects.requireNonNull(fromName, "fromName must not be null");
        Objects.requireNonNull(toName, "toName must not be null");
        Objects.requireNonNull(changedByName, "changedByName must not be null");
    }
}
