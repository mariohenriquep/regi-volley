package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LisbonMonthTest {

    @Test
    void aSummerInstantJustBeforeLisbonMidnightBelongsToTheNextMonth() {
        // Arrange - 31 Aug 23:30Z is 1 Sep 00:30 in Lisbon (WEST, UTC+1)
        Instant instant = Instant.parse("2026-08-31T23:30:00Z");

        // Act
        LisbonMonth month = LisbonMonth.containing(instant);

        // Assert
        assertThat(month.start()).isEqualTo(Instant.parse("2026-08-31T23:00:00Z"));
        assertThat(month.endExclusive()).isEqualTo(Instant.parse("2026-09-30T23:00:00Z"));
        assertThat(month.contains(instant)).isTrue();
    }

    @Test
    void theLastLisbonMinuteOfAMonthIsStillInThatMonth() {
        // Arrange - 31 Aug 22:59:59Z is 31 Aug 23:59:59 Lisbon
        Instant instant = Instant.parse("2026-08-31T22:59:59Z");

        // Act
        LisbonMonth august = LisbonMonth.containing(instant);

        // Assert
        assertThat(august.start()).isEqualTo(Instant.parse("2026-07-31T23:00:00Z"));
        assertThat(august.endExclusive()).isEqualTo(Instant.parse("2026-08-31T23:00:00Z"));
        assertThat(august.contains(Instant.parse("2026-08-31T23:00:00Z"))).isFalse();
        assertThat(august.contains(instant)).isTrue();
    }

    @Test
    void inWinterLisbonAndUtcCoincide() {
        // Arrange
        Instant instant = Instant.parse("2026-12-31T23:30:00Z");

        // Act
        LisbonMonth december = LisbonMonth.containing(instant);

        // Assert
        assertThat(december.start()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
        assertThat(december.endExclusive()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    }

    @Test
    void aMonthWithAClockChangeKeepsLisbonMidnights() {
        // Arrange - October 2026: clocks go back on the 25th, so the month is 745 hours long
        Instant instant = Instant.parse("2026-10-12T09:00:00Z");

        // Act
        LisbonMonth october = LisbonMonth.containing(instant);

        // Assert
        assertThat(october.start()).isEqualTo(Instant.parse("2026-09-30T23:00:00Z"));
        assertThat(october.endExclusive()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void containsIsHalfOpen() {
        // Arrange
        LisbonMonth october = LisbonMonth.containing(Instant.parse("2026-10-12T09:00:00Z"));

        // Act
        boolean atStart = october.contains(october.start());
        boolean beforeStart = october.contains(october.start().minusSeconds(1));
        boolean atEnd = october.contains(october.endExclusive());

        // Assert
        assertThat(atStart).isTrue();
        assertThat(beforeStart).isFalse();
        assertThat(atEnd).isFalse();
    }
}
