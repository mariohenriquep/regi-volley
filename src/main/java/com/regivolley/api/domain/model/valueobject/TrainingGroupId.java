package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record TrainingGroupId(UUID value) implements ValueObject {

    public TrainingGroupId {
        Objects.requireNonNull(value, "TrainingGroupId value must not be null");
    }

    public static TrainingGroupId generate() {
        return new TrainingGroupId(UUID.randomUUID());
    }

    public static TrainingGroupId of(UUID value) {
        return new TrainingGroupId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
