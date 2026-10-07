package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One booking that holds a place in a {@code Subscription}'s balance (RN-15), with the session's
 * Europe/Lisbon calendar date so weekly allowances can be counted per week.
 */
public record CreditUsage(BookingId bookingId, LocalDate sessionDate) implements ValueObject {

    public CreditUsage {
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        Objects.requireNonNull(sessionDate, "sessionDate must not be null");
    }
}
