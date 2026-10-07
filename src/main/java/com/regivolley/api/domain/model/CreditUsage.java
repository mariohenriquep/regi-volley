package com.regivolley.api.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One booking that holds a place in a {@link Subscription}'s balance (RN-15), with the session's
 * Europe/Lisbon calendar date so weekly allowances can be counted per week.
 */
public record CreditUsage(BookingId bookingId, LocalDate sessionDate) {

    public CreditUsage {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(sessionDate, "sessionDate must not be null");
    }
}
