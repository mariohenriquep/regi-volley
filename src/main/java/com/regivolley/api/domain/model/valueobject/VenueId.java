package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed identifier of a venue (Pavilhao). The {@code Venue} aggregate itself is outside Phase 1
 * (no US asks to manage venues yet); a training group only points at one by id.
 */
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
