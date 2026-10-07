package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScheduledOccurrenceTest {

    private static final Instant START = Instant.parse("2026-01-12T20:00:00Z");

    @Test
    void holdsTheStartAndEnd() {
        // Arrange
        Instant end = START.plusSeconds(3600);

        // Act
        ScheduledOccurrence occurrence = new ScheduledOccurrence(START, end);

        // Assert
        assertThat(occurrence.startsAt()).isEqualTo(START);
        assertThat(occurrence.endsAt()).isEqualTo(end);
    }

    @Test
    void rejectsAnEndThatIsNotAfterTheStart() {
        // Arrange
        Executable act = () -> new ScheduledOccurrence(START, START);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("after");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullArguments")
    void rejectsNulls(String field, Executable act) {
        // Arrange
        // (act from the source)

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains(field);
    }

    static Stream<Arguments> nullArguments() {
        return Stream.of(
                Arguments.of("startsAt", (Executable) () -> new ScheduledOccurrence(null, START)),
                Arguments.of("endsAt", (Executable) () -> new ScheduledOccurrence(START, null)));
    }
}
