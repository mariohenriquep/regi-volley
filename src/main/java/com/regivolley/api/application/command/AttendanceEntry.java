package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.BookingId;

import java.util.Objects;

/** One person's attendance: the booking and whether they came. */
public record AttendanceEntry(BookingId bookingId, AttendanceMark mark) {

    public AttendanceEntry {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(mark, "mark must not be null");
    }
}
