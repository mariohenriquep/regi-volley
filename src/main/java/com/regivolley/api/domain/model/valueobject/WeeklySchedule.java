package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidScheduleException;
import com.regivolley.api.domain.shared.ValueObject;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The recurring schedule of a training group (US-09, RN-01): one or more weekly slots, none of
 * which overlap. Slots are kept ordered by weekday then start time so equal schedules compare equal.
 */
public record WeeklySchedule(List<WeeklySlot> slots) implements ValueObject {

    private static final Comparator<WeeklySlot> CHRONOLOGICAL =
            Comparator.comparing(WeeklySlot::dayOfWeek).thenComparing(WeeklySlot::startTime);

    public WeeklySchedule {
        Objects.requireNonNull(slots, "slots must not be null");
        if (slots.isEmpty()) {
            throw new InvalidScheduleException("A schedule needs at least one weekly slot");
        }
        slots = slots.stream().sorted(CHRONOLOGICAL).toList();
        requireNoOverlap(slots);
    }

    public static WeeklySchedule of(WeeklySlot... slots) {
        return new WeeklySchedule(List.of(slots));
    }

    /**
     * Every occurrence that starts in {@code [fromInclusive, toExclusive)}, in chronological
     * order. A slot exactly at {@code fromInclusive} is included, one exactly at {@code toExclusive} is not.
     */
    public List<ScheduledOccurrence> occurrencesBetween(Instant fromInclusive, Instant toExclusive) {
        LocalDate firstDay = fromInclusive.atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();
        LocalDate lastDay = toExclusive.atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();
        List<ScheduledOccurrence> occurrences = new ArrayList<>();
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            for (WeeklySlot slot : slots) {
                if (slot.dayOfWeek() == day.getDayOfWeek()) {
                    ScheduledOccurrence occurrence = slot.occurrenceOn(day);
                    if (!occurrence.startsAt().isBefore(fromInclusive) && occurrence.startsAt().isBefore(toExclusive)) {
                        occurrences.add(occurrence);
                    }
                }
            }
        }
        occurrences.sort(Comparator.comparing(ScheduledOccurrence::startsAt));
        return List.copyOf(occurrences);
    }

    private static void requireNoOverlap(List<WeeklySlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                if (slots.get(i).overlaps(slots.get(j))) {
                    throw new InvalidScheduleException("Schedule slots overlap: " + describe(slots.get(i))
                            + " and " + describe(slots.get(j)));
                }
            }
        }
    }

    private static String describe(WeeklySlot slot) {
        return slot.dayOfWeek() + " " + slot.startTime();
    }
}
