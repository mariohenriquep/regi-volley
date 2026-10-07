package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.time.Duration;
import java.time.Instant;

/**
 * Per-association booking timing rules (RN-03, RN-10). Configurable per association later; for
 * now callers pass it in.
 *
 * @param bookingWindowDays      how many days before the session start bookings open (default 7)
 * @param freeCancellationHours  how many hours before the start free cancellation ends (default 6)
 */
public record BookingPolicy(int bookingWindowDays, int freeCancellationHours) implements ValueObject {

    public static final int DEFAULT_BOOKING_WINDOW_DAYS = 7;
    public static final int DEFAULT_FREE_CANCELLATION_HOURS = 6;

    public BookingPolicy {
        if (bookingWindowDays < 1) {
            throw new IllegalArgumentException("bookingWindowDays must be at least 1");
        }
        if (freeCancellationHours < 0) {
            throw new IllegalArgumentException("freeCancellationHours must not be negative");
        }
    }

    public static BookingPolicy defaults() {
        return new BookingPolicy(DEFAULT_BOOKING_WINDOW_DAYS, DEFAULT_FREE_CANCELLATION_HOURS);
    }

    /**
     * Instant bookings open: N calendar days before the start, at the same Lisbon wall-clock time,
     * so a 20:00 class opens at 20:00 even if the clocks change in between.
     */
    public Instant bookingOpensAt(Instant sessionStart) {
        return sessionStart.atZone(ScheduleZone.LISBON.zoneId()).minusDays(bookingWindowDays).toInstant();
    }

    /** Last instant (inclusive) at which a cancellation is still free. */
    public Instant freeCancellationDeadline(Instant sessionStart) {
        return sessionStart.minus(Duration.ofHours(freeCancellationHours));
    }
}
