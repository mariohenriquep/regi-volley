package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleZoneTest {

    @Test
    void lisbonIsTheEuropeLisbonZone() {
        // Arrange
        // (no input)

        // Act
        ZoneId zone = ScheduleZone.LISBON.zoneId();

        // Assert
        assertThat(zone).isEqualTo(ZoneId.of("Europe/Lisbon"));
    }

    @Test
    void followsDaylightSavingTime() {
        // Arrange
        Instant winter = Instant.parse("2026-01-12T20:00:00Z");
        Instant summer = Instant.parse("2026-07-13T20:00:00Z");

        // Act
        int winterHour = winter.atZone(ScheduleZone.LISBON.zoneId()).getHour();
        int summerHour = summer.atZone(ScheduleZone.LISBON.zoneId()).getHour();

        // Assert
        assertThat(winterHour).isEqualTo(20);
        assertThat(summerHour).isEqualTo(21);
    }
}
