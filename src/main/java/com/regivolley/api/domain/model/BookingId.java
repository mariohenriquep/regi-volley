package com.regivolley.api.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record BookingId(UUID value) {

    public BookingId {
        Objects.requireNonNull(value, "BookingId value must not be null");
    }

    public static BookingId generate() {
        return new BookingId(UUID.randomUUID());
    }

    public static BookingId of(UUID value) {
        return new BookingId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
