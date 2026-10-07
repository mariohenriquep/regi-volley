package com.regivolley.api.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void theProductionClockIsInUtc() {
        // Arrange
        ClockConfig config = new ClockConfig();

        // Act
        Clock clock = config.clock();

        // Assert
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void theProductionClockNeverReturnsMoreThanMicrosecondPrecision() {
        // Arrange - PostgreSQL stores microseconds, so a finer instant would not survive a round trip
        Clock clock = new ClockConfig().clock();

        // Act
        Instant[] samples = new Instant[2_000];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = clock.instant();
        }

        // Assert
        assertThat(samples).allSatisfy(instant -> assertThat(instant.getNano() % 1_000).isZero());
    }

    @Test
    void theProductionClockStillMovesForward() {
        // Arrange
        Clock clock = new ClockConfig().clock();
        Instant before = Instant.now().minusSeconds(1);

        // Act
        Instant now = clock.instant();

        // Assert
        assertThat(now).isAfter(before);
    }
}
