package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record AssociationId(UUID value) implements ValueObject {

    public AssociationId {
        Objects.requireNonNull(value, "AssociationId value must not be null");
    }

    public static AssociationId generate() {
        return new AssociationId(UUID.randomUUID());
    }

    public static AssociationId of(UUID value) {
        return new AssociationId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
