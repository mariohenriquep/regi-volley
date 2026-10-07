package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record PlanId(UUID value) implements ValueObject {

    public PlanId {
        Objects.requireNonNull(value, "PlanId value must not be null");
    }

    public static PlanId generate() {
        return new PlanId(UUID.randomUUID());
    }

    public static PlanId of(UUID value) {
        return new PlanId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
