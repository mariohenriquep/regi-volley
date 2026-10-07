package com.regivolley.api.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record JoinRequestId(UUID value) {

    public JoinRequestId {
        Objects.requireNonNull(value, "JoinRequestId value must not be null");
    }

    public static JoinRequestId generate() {
        return new JoinRequestId(UUID.randomUUID());
    }

    public static JoinRequestId of(UUID value) {
        return new JoinRequestId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
