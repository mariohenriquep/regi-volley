package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier of a venue (Pavilhao, US-02); a training group points at its {@code Venue} by this id. */
public record VenueId(UUID value) implements ValueObject {

    public VenueId {
        Objects.requireNonNull(value, "VenueId value must not be null");
    }

    public static VenueId generate() {
        return new VenueId(UUID.randomUUID());
    }

    public static VenueId of(UUID value) {
        return new VenueId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
