package com.regivolley.api.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * One entry of a member's level history (US-07): who moved them, when, from which level to which.
 * Holds ids only, so it stays valid, and free of personal data, after an anonymisation.
 */
public record LevelChange(LevelId from, LevelId to, MemberId changedBy, Instant changedAt) {

    public LevelChange {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(changedBy, "changedBy must not be null");
        Objects.requireNonNull(changedAt, "changedAt must not be null");
        if (from.equals(to)) {
            throw new IllegalArgumentException("A level change must change the level");
        }
    }
}
