package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidScheduleException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeeklyScheduleTest {

    private static final WeeklySlot MONDAY = slot(DayOfWeek.MONDAY, 20, 0, 90);
    private static final WeeklySlot WEDNESDAY = slot(DayOfWeek.WEDNESDAY, 21, 0, 90);

    @Nested
    class Creation {

        @Test
        void holdsOneOrMoreSlotsOrderedByDayAndTime() {
            // Arrange
            WeeklySlot early = slot(DayOfWeek.WEDNESDAY, 18, 0, 60);

            // Act
            WeeklySchedule schedule = WeeklySchedule.of(WEDNESDAY, MONDAY, early);

            // Assert
            assertThat(schedule.slots()).containsExactly(MONDAY, early, WEDNESDAY);
        }

        @Test
        void rejectsAnEmptySchedule() {
            // Arrange
            Executable act = () -> new WeeklySchedule(List.of());

            // Act
            InvalidScheduleException ex = assertThrows(InvalidScheduleException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("at least one");
        }

        @Test
        void rejectsOverlappingSlotsOnTheSameDay() {
            // Arrange
            Executable act = () -> WeeklySchedule.of(MONDAY, slot(DayOfWeek.MONDAY, 21, 0, 60));

            // Act
            InvalidScheduleException ex = assertThrows(InvalidScheduleException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("overlap").contains("MONDAY");
        }

        @Test
        void rejectsASlotRunningPastMidnightIntoAnotherSlot() {
            // Arrange
            Executable act = () -> WeeklySchedule.of(slot(DayOfWeek.SUNDAY, 23, 0, 120), slot(DayOfWeek.MONDAY, 0, 30, 60));

            // Act
            InvalidScheduleException ex = assertThrows(InvalidScheduleException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("overlap");
        }

        @Test
        void acceptsBackToBackSlots() {
            // Arrange
            WeeklySlot next = slot(DayOfWeek.MONDAY, 21, 30, 60);

            // Act
            WeeklySchedule schedule = WeeklySchedule.of(MONDAY, next);

            // Assert
            assertThat(schedule.slots()).hasSize(2);
        }

        @Test
        void rejectsANullList() {
            // Arrange
            Executable act = () -> new WeeklySchedule(null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("slots");
        }

        @Test
        void rejectsANullSlot() {
            // Arrange
            Executable act = () -> WeeklySchedule.of(MONDAY, null);

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex).isNotNull();
        }

        @Test
        void isImmutable() {
            // Arrange
            WeeklySchedule schedule = WeeklySchedule.of(MONDAY);

            // Act
            Executable act = () -> schedule.slots().add(WEDNESDAY);

            // Assert
            assertThrows(UnsupportedOperationException.class, act);
        }
    }

    @Nested
    class OccurrencesBetween {

        @Test
        void listsEverySlotOccurrenceInChronologicalOrder() {
            // Arrange - Mon 12/01/2026 00:00Z to Mon 26/01/2026 00:00Z
            WeeklySchedule schedule = WeeklySchedule.of(WEDNESDAY, MONDAY);

            // Act
            List<ScheduledOccurrence> occurrences = schedule.occurrencesBetween(
                    Instant.parse("2026-01-12T00:00:00Z"), Instant.parse("2026-01-26T00:00:00Z"));

            // Assert
            assertThat(occurrences).extracting(o -> o.startsAt().toString()).containsExactly(
                    "2026-01-12T20:00:00Z", "2026-01-14T21:00:00Z", "2026-01-19T20:00:00Z", "2026-01-21T21:00:00Z");
        }

        @Test
        void includesAnOccurrenceExactlyAtTheStartAndExcludesOneExactlyAtTheEnd() {
            // Arrange
            WeeklySchedule schedule = WeeklySchedule.of(MONDAY);
            Instant from = Instant.parse("2026-01-12T20:00:00Z");
            Instant to = Instant.parse("2026-01-26T20:00:00Z");

            // Act
            List<ScheduledOccurrence> occurrences = schedule.occurrencesBetween(from, to);

            // Assert
            assertThat(occurrences).extracting(ScheduledOccurrence::startsAt)
                    .containsExactly(from, Instant.parse("2026-01-19T20:00:00Z"));
        }

        @Test
        void isEmptyWhenTheRangeIsEmpty() {
            // Arrange
            WeeklySchedule schedule = WeeklySchedule.of(MONDAY);
            Instant at = Instant.parse("2026-01-12T20:00:00Z");

            // Act
            List<ScheduledOccurrence> occurrences = schedule.occurrencesBetween(at, at);

            // Assert
            assertThat(occurrences).isEmpty();
        }
    }

    private static WeeklySlot slot(DayOfWeek day, int hour, int minute, long minutes) {
        return new WeeklySlot(day, LocalTime.of(hour, minute), Duration.ofMinutes(minutes));
    }
}
