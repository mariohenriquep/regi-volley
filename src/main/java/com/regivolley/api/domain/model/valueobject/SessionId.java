package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record SessionId(UUID value) implements ValueObject {

    public SessionId {
        Objects.requireNonNull(value, "SessionId value must not be null");
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }

    public static SessionId of(UUID value) {
        return new SessionId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
