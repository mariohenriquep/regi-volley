package com.regivolley.api.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingPolicyTest {

    @Test
    void defaultsToSevenDaysAndSixHours() {
        // Arrange
        // (no input)

        // Act
        BookingPolicy policy = BookingPolicy.defaults();

        // Assert
        assertThat(policy.bookingWindowDays()).isEqualTo(7);
        assertThat(policy.freeCancellationHours()).isEqualTo(6);
    }

    @Test
    void rejectsAWindowShorterThanOneDay() {
        // Arrange
        Executable act = () -> new BookingPolicy(0, 6);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("bookingWindowDays");
    }

    @Test
    void rejectsNegativeFreeCancellationHours() {
        // Arrange
        Executable act = () -> new BookingPolicy(7, -1);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("freeCancellationHours");
    }

    @Test
    void allowsZeroFreeCancellationHours() {
        // Arrange
        BookingPolicy policy = new BookingPolicy(7, 0);
        Instant start = Instant.parse("2026-10-20T19:00:00Z");

        // Act
        Instant deadline = policy.freeCancellationDeadline(start);

        // Assert
        assertThat(deadline).isEqualTo(start);
    }

    @Test
    void opensSevenDaysBeforeAtTheSameLisbonWallClockTime() {
        // Arrange
        Instant start = Instant.parse("2026-10-20T19:00:00Z");

        // Act
        Instant opensAt = BookingPolicy.defaults().bookingOpensAt(start);

        // Assert
        assertThat(opensAt).isEqualTo(Instant.parse("2026-10-13T19:00:00Z"));
    }

    @Test
    void keepsTheLisbonWallClockTimeAcrossTheSpringDstChange() {
        // Arrange - 20:00 Lisbon on 29 Mar 2026 is 19:00Z (WEST); a week earlier it was 20:00Z (WET)
        Instant start = Instant.parse("2026-03-29T19:00:00Z");

        // Act
        Instant opensAt = BookingPolicy.defaults().bookingOpensAt(start);

        // Assert
        assertThat(opensAt).isEqualTo(Instant.parse("2026-03-22T20:00:00Z"));
    }

    @Test
    void freeCancellationDeadlineIsAnAbsoluteNumberOfHoursBeforeTheStart() {
        // Arrange
        Instant start = Instant.parse("2026-10-20T19:00:00Z");

        // Act
        Instant deadline = BookingPolicy.defaults().freeCancellationDeadline(start);

        // Assert
        assertThat(deadline).isEqualTo(Instant.parse("2026-10-20T13:00:00Z"));
    }
}
