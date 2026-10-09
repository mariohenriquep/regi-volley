package com.regivolley.api.application.result;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/** One weekly slot of a group, in the association's local time (Europe/Lisbon). */
public record PublicSlot(DayOfWeek dayOfWeek, LocalTime startTime, int durationMinutes) {

    public PublicSlot {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
    }
}
