package com.regivolley.api.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record LevelId(UUID value) {

    public LevelId {
        Objects.requireNonNull(value, "LevelId value must not be null");
    }

    public static LevelId generate() {
        return new LevelId(UUID.randomUUID());
    }

    public static LevelId of(UUID value) {
        return new LevelId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
