package com.regivolley.api.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record MemberId(UUID value) {

    public MemberId {
        Objects.requireNonNull(value, "MemberId value must not be null");
    }

    public static MemberId generate() {
        return new MemberId(UUID.randomUUID());
    }

    public static MemberId of(UUID value) {
        return new MemberId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
