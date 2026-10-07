package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.time.Instant;
import java.util.Objects;

/** One concrete run of a weekly slot, as UTC instants: when a generated session starts and ends. */
public record ScheduledOccurrence(Instant startsAt, Instant endsAt) implements ValueObject {

    public ScheduledOccurrence {
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("An occurrence must end after it starts");
        }
    }
}
