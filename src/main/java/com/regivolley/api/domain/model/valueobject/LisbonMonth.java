package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A calendar month in Europe/Lisbon as the half-open instant range {@code [start, endExclusive)}
 * (architecture.md section 9): the month a no-show falls in (RN-11) is the association's, not UTC's, so
 * a session at 23:30Z on 31 August in summer time already counts in September.
 */
public record LisbonMonth(Instant start, Instant endExclusive) implements ValueObject {

    public LisbonMonth {
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(endExclusive, "endExclusive must not be null");
    }

    /** The Lisbon calendar month that contains {@code instant}. */
    public static LisbonMonth containing(Instant instant) {
        var zone = ScheduleZone.LISBON.zoneId();
        LocalDate first = instant.atZone(zone).toLocalDate().withDayOfMonth(1);
        return new LisbonMonth(first.atStartOfDay(zone).toInstant(), first.plusMonths(1).atStartOfDay(zone).toInstant());
    }

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(endExclusive);
    }
}
