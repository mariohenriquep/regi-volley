package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidScheduleException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeeklySlotTest {

    private static final LocalTime EIGHT_PM = LocalTime.of(20, 0);
    private static final Duration NINETY_MINUTES = Duration.ofMinutes(90);

    @Nested
    class Creation {

        @Test
        void createsASlot() {
            // Arrange
            // (no input)

            // Act
            WeeklySlot slot = new WeeklySlot(DayOfWeek.TUESDAY, EIGHT_PM, NINETY_MINUTES);

            // Assert
            assertThat(slot.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(slot.startTime()).isEqualTo(EIGHT_PM);
            assertThat(slot.duration()).isEqualTo(NINETY_MINUTES);
        }

        @ParameterizedTest
        @ValueSource(longs = {1, 90, 240})
        void acceptsDurationsFromOneMinuteToFourHours(long minutes) {
            // Arrange
            Duration duration = Duration.ofMinutes(minutes);

            // Act
            WeeklySlot slot = new WeeklySlot(DayOfWeek.MONDAY, EIGHT_PM, duration);

            // Assert
            assertThat(slot.duration()).isEqualTo(duration);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -30, 241, 600})
        void rejectsDurationsOutsideTheBounds(long minutes) {
            // Arrange
            Executable act = () -> new WeeklySlot(DayOfWeek.MONDAY, EIGHT_PM, Duration.ofMinutes(minutes));

            // Act
            InvalidScheduleException ex = assertThrows(InvalidScheduleException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("duration");
        }

        @Test
        void rejectsDurationsThatAreNotWholeMinutes() {
            // Arrange
            Executable act = () -> new WeeklySlot(DayOfWeek.MONDAY, EIGHT_PM, Duration.ofSeconds(90 * 60 + 1));

            // Act
            InvalidScheduleException ex = assertThrows(InvalidScheduleException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("whole minutes");
        }

        @Test
        void rejectsStartTimesWithSeconds() {
            // Arrange
            Executable act = () -> new WeeklySlot(DayOfWeek.MONDAY, LocalTime.of(20, 0, 30), NINETY_MINUTES);

            // Act
            InvalidScheduleException ex = assertThrows(InvalidScheduleException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("whole minutes");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("nullArguments")
        void rejectsNullFields(String field, Executable act) {
            // Arrange
            // (act from the source)

            // Act
            NullPointerException ex = assertThrows(NullPointerException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains(field);
        }

        static Stream<Arguments> nullArguments() {
            return Stream.of(
                    Arguments.of("dayOfWeek", (Executable) () -> new WeeklySlot(null, EIGHT_PM, NINETY_MINUTES)),
                    Arguments.of("startTime", (Executable) () -> new WeeklySlot(DayOfWeek.MONDAY, null, NINETY_MINUTES)),
                    Arguments.of("duration", (Executable) () -> new WeeklySlot(DayOfWeek.MONDAY, EIGHT_PM, null)));
        }
    }

    @Nested
    class OccurrenceOn {

        @Test
        void keepsTheLisbonWallClockTimeInWinterAndSummer() {
            // Arrange
            WeeklySlot slot = new WeeklySlot(DayOfWeek.MONDAY, EIGHT_PM, NINETY_MINUTES);

            // Act
            ScheduledOccurrence winter = slot.occurrenceOn(LocalDate.of(2026, 1, 12));
            ScheduledOccurrence summer = slot.occurrenceOn(LocalDate.of(2026, 7, 13));

            // Assert
            assertThat(winter.startsAt().toString()).isEqualTo("2026-01-12T20:00:00Z");
            assertThat(winter.endsAt().toString()).isEqualTo("2026-01-12T21:30:00Z");
            assertThat(summer.startsAt().toString()).isEqualTo("2026-07-13T19:00:00Z");
            assertThat(summer.endsAt().toString()).isEqualTo("2026-07-13T20:30:00Z");
        }

        @Test
        void movesASlotInsideTheSpringForwardGapToTheFirstInstantAfterIt() {
            // Arrange - 29/03/2026 01:00 Lisbon jumps to 02:00, so 01:30 does not exist
            WeeklySlot slot = new WeeklySlot(DayOfWeek.SUNDAY, LocalTime.of(1, 30), NINETY_MINUTES);

            // Act
            ScheduledOccurrence occurrence = slot.occurrenceOn(LocalDate.of(2026, 3, 29));

            // Assert - 02:30 WEST
            assertThat(occurrence.startsAt().toString()).isEqualTo("2026-03-29T01:30:00Z");
            assertThat(occurrence.endsAt().toString()).isEqualTo("2026-03-29T03:00:00Z");
        }

        @Test
        void picksTheEarlierOffsetForASlotInsideTheFallBackOverlap() {
            // Arrange - 25/10/2026 02:00 WEST becomes 01:00 WET, so 01:30 happens twice
            WeeklySlot slot = new WeeklySlot(DayOfWeek.SUNDAY, LocalTime.of(1, 30), NINETY_MINUTES);

            // Act
            ScheduledOccurrence occurrence = slot.occurrenceOn(LocalDate.of(2026, 10, 25));

            // Assert - the first 01:30, in WEST (UTC+1)
            assertThat(occurrence.startsAt().toString()).isEqualTo("2026-10-25T00:30:00Z");
        }

        @Test
        void lastsTheExactDurationEvenAcrossAClockChange() {
            // Arrange - 00:30 on the fall-back night runs through the repeated hour
            WeeklySlot slot = new WeeklySlot(DayOfWeek.SUNDAY, LocalTime.of(0, 30), Duration.ofHours(3));

            // Act
            ScheduledOccurrence occurrence = slot.occurrenceOn(LocalDate.of(2026, 10, 25));

            // Assert
            assertThat(Duration.between(occurrence.startsAt(), occurrence.endsAt())).isEqualTo(Duration.ofHours(3));
        }

        @Test
        void rejectsADateOnAnotherWeekday() {
            // Arrange
            WeeklySlot slot = new WeeklySlot(DayOfWeek.MONDAY, EIGHT_PM, NINETY_MINUTES);
            Executable act = () -> slot.occurrenceOn(LocalDate.of(2026, 1, 13));

            // Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

            // Assert
            assertThat(ex.getMessage()).contains("MONDAY");
        }
    }

    @Nested
    class Overlap {

        static Stream<Arguments> overlappingPairs() {
            return Stream.of(
                    Arguments.of(slot(DayOfWeek.MONDAY, 20, 0, 90), slot(DayOfWeek.MONDAY, 21, 0, 60)),
                    Arguments.of(slot(DayOfWeek.MONDAY, 20, 0, 90), slot(DayOfWeek.MONDAY, 20, 0, 90)),
                    Arguments.of(slot(DayOfWeek.MONDAY, 20, 0, 120), slot(DayOfWeek.MONDAY, 20, 30, 15)),
                    Arguments.of(slot(DayOfWeek.MONDAY, 23, 0, 120), slot(DayOfWeek.TUESDAY, 0, 30, 60)),
                    Arguments.of(slot(DayOfWeek.SUNDAY, 23, 30, 120), slot(DayOfWeek.MONDAY, 0, 0, 30))
            );
        }

        static Stream<Arguments> disjointPairs() {
            return Stream.of(
                    Arguments.of(slot(DayOfWeek.MONDAY, 20, 0, 60), slot(DayOfWeek.MONDAY, 21, 0, 60)),
                    Arguments.of(slot(DayOfWeek.MONDAY, 20, 0, 60), slot(DayOfWeek.TUESDAY, 20, 0, 60)),
                    Arguments.of(slot(DayOfWeek.MONDAY, 23, 0, 60), slot(DayOfWeek.TUESDAY, 0, 0, 60)),
                    Arguments.of(slot(DayOfWeek.SUNDAY, 23, 0, 60), slot(DayOfWeek.MONDAY, 0, 0, 60))
            );
        }

        @ParameterizedTest
        @MethodSource("overlappingPairs")
        void detectsOverlapInBothDirections(WeeklySlot a, WeeklySlot b) {
            // Arrange
            // (slots from the source)

            // Act
            boolean aOverlapsB = a.overlaps(b);
            boolean bOverlapsA = b.overlaps(a);

            // Assert
            assertThat(aOverlapsB).isTrue();
            assertThat(bOverlapsA).isTrue();
        }

        @ParameterizedTest
        @MethodSource("disjointPairs")
        void slotsThatMerelyTouchDoNotOverlap(WeeklySlot a, WeeklySlot b) {
            // Arrange
            // (slots from the source)

            // Act
            boolean aOverlapsB = a.overlaps(b);
            boolean bOverlapsA = b.overlaps(a);

            // Assert
            assertThat(aOverlapsB).isFalse();
            assertThat(bOverlapsA).isFalse();
        }
    }

    private static WeeklySlot slot(DayOfWeek day, int hour, int minute, long minutes) {
        return new WeeklySlot(day, LocalTime.of(hour, minute), Duration.ofMinutes(minutes));
    }
}
