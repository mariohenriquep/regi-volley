package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.valueobject.ScheduleZone;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * A Lisbon calendar week as the half-open instant range {@code [from, toExclusive)}: Monday 00:00 to the next Monday 00:00 in
 * {@code Europe/Lisbon}, so a week with a clock change is an hour longer or shorter. Shared by the week views.
 */
record LisbonWeek(Instant from, Instant toExclusive) {

    /** The week that contains {@code day}; the current one (by the clock) when it is null. */
    static LisbonWeek containing(LocalDate day, Clock clock) {
        var zone = ScheduleZone.LISBON.zoneId();
        LocalDate monday = Optional.ofNullable(day)
                .orElseGet(() -> clock.instant().atZone(zone).toLocalDate())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LisbonWeek(monday.atStartOfDay(zone).toInstant(), monday.plusWeeks(1).atStartOfDay(zone).toInstant());
    }
}
