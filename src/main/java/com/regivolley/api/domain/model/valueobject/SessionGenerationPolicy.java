package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.time.Instant;

/**
 * How far ahead sessions are generated (RN-01): the window is {@code windowWeeks} calendar weeks
 * in Europe/Lisbon, so it ends at the same wall-clock time even when the clocks change inside it.
 *
 * <p>Kept apart from {@link BookingPolicy}: that one carries booking and cancellation timing and
 * is handed to {@code Session.book}, whereas generation has a single input and its own callers.
 * It is configurable per association (default 4 weeks) once the association stores it; until
 * then the caller passes it in.
 *
 * @param windowWeeks between 1 and {@value #MAX_WINDOW_WEEKS}
 */
public record SessionGenerationPolicy(int windowWeeks) implements ValueObject {

    public static final int DEFAULT_WINDOW_WEEKS = 4;
    public static final int MAX_WINDOW_WEEKS = 12;

    public SessionGenerationPolicy {
        if (windowWeeks < 1 || windowWeeks > MAX_WINDOW_WEEKS) {
            throw new IllegalArgumentException("windowWeeks must be between 1 and " + MAX_WINDOW_WEEKS);
        }
    }

    public static SessionGenerationPolicy defaults() {
        return new SessionGenerationPolicy(DEFAULT_WINDOW_WEEKS);
    }

    /** Exclusive end of the window that starts at {@code from}. */
    public Instant windowEnd(Instant from) {
        return from.atZone(ScheduleZone.LISBON.zoneId()).plusWeeks(windowWeeks).toInstant();
    }
}
