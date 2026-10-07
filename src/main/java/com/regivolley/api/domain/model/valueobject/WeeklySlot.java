package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidScheduleException;
import com.regivolley.api.domain.shared.ValueObject;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * One recurring weekly time of a training group: a weekday, a local start time in Europe/Lisbon
 * (architecture.md section 9) and a duration. Start and duration are whole minutes and the
 * duration is between one minute and {@value #MAX_DURATION_MINUTES} minutes.
 *
 * <p><b>Clock changes</b> when the slot is turned into instants ({@link #occurrenceOn}):
 * <ul>
 *   <li>Spring-forward gap (local time that does not exist, e.g. 01:30 on 29/03/2026): follows
 *       {@link ZonedDateTime#of}, which moves the time forward by the length of the gap (to
 *       02:30). The session still happens, one wall-clock hour later, rather than silently
 *       vanishing for that week; the generated instant is the same on every run, so
 *       de-duplication still holds.</li>
 *   <li>Fall-back overlap (local time that happens twice, e.g. 01:30 on 25/10/2026): the earlier
 *       offset (the first occurrence, still summer time) is used, also what {@code ZonedDateTime.of} does.</li>
 *   <li>The session lasts exactly {@link #duration()} of elapsed time, even across a change.</li>
 * </ul>
 *
 * <p>{@link #overlaps} compares wall-clock times, so two slots that merely touch (one starts when
 * the other ends) are accepted, yet on the spring-forward night the first can be shifted to
 * the very instant the second starts (01:30-02:30 and 02:30-03:30 on 29/03/2026). They then
 * genuinely coincide that night; session generation emits a single session for that instant.
 */
public record WeeklySlot(DayOfWeek dayOfWeek, LocalTime startTime, Duration duration) implements ValueObject {

    public static final long MAX_DURATION_MINUTES = 240;

    private static final long MINUTES_PER_DAY = 24 * 60;
    private static final long MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

    public WeeklySlot {
        Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(duration, "duration must not be null");
        if (startTime.getSecond() != 0 || startTime.getNano() != 0 || duration.toSecondsPart() != 0
                || duration.toNanosPart() != 0) {
            throw new InvalidScheduleException("A slot's start time and duration must be whole minutes");
        }
        if (duration.isNegative() || duration.isZero() || duration.toMinutes() > MAX_DURATION_MINUTES) {
            throw new InvalidScheduleException(
                    "A slot's duration must be between 1 minute and " + MAX_DURATION_MINUTES / 60 + " hours");
        }
    }

    /** The concrete start and end of this slot on {@code date}, which must fall on the slot's weekday. */
    public ScheduledOccurrence occurrenceOn(LocalDate date) {
        if (date.getDayOfWeek() != dayOfWeek) {
            throw new IllegalArgumentException(date + " is not a " + dayOfWeek);
        }
        ZonedDateTime start = ZonedDateTime.of(date, startTime, ScheduleZone.LISBON.zoneId());
        return new ScheduledOccurrence(start.toInstant(), start.plus(duration).toInstant());
    }

    /**
     * Whether the two slots share any time within the week. Slots that only touch (one ends when
     * the other starts) do not overlap; a slot running past midnight is compared across the
     * Sunday-to-Monday wrap too.
     */
    public boolean overlaps(WeeklySlot other) {
        long start = minuteOfWeek();
        long end = start + duration.toMinutes();
        for (long shift : new long[]{-MINUTES_PER_WEEK, 0, MINUTES_PER_WEEK}) {
            long otherStart = other.minuteOfWeek() + shift;
            long otherEnd = otherStart + other.duration.toMinutes();
            if (start < otherEnd && otherStart < end) {
                return true;
            }
        }
        return false;
    }

    private long minuteOfWeek() {
        return (dayOfWeek.getValue() - 1) * MINUTES_PER_DAY + startTime.getHour() * 60L + startTime.getMinute();
    }
}
