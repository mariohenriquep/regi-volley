package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionGenerationPolicyTest {

    @Test
    void defaultsToFourWeeks() {
        // Arrange
        // (no input)

        // Act
        SessionGenerationPolicy policy = SessionGenerationPolicy.defaults();

        // Assert
        assertThat(policy.windowWeeks()).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 12})
    void acceptsWindowsFromOneToTwelveWeeks(int weeks) {
        // Arrange
        // (weeks from the source)

        // Act
        SessionGenerationPolicy policy = new SessionGenerationPolicy(weeks);

        // Assert
        assertThat(policy.windowWeeks()).isEqualTo(weeks);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 13})
    void rejectsWindowsOutsideTheBounds(int weeks) {
        // Arrange
        Executable act = () -> new SessionGenerationPolicy(weeks);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("windowWeeks");
    }

    @Test
    void windowEndsFourLisbonCalendarWeeksAfterTheStart() {
        // Arrange - Monday 16/03/2026 20:00 Lisbon (WET) ... DST starts on 29/03
        Instant from = Instant.parse("2026-03-16T20:00:00Z");

        // Act
        Instant end = SessionGenerationPolicy.defaults().windowEnd(from);

        // Assert - Monday 13/04/2026 20:00 Lisbon (WEST) is 19:00Z
        assertThat(end).isEqualTo(Instant.parse("2026-04-13T19:00:00Z"));
    }
}
