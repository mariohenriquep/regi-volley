package com.regivolley.api.infrastructure.web;

import com.regivolley.api.infrastructure.web.dto.PlausibleDateValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Threat model S4: a date a client types is bounded, so no query or period can be built from year 0 or year 999999999. */
class PlausibleDateTest {

    private final PlausibleDateValidator validator = new PlausibleDateValidator();

    @ParameterizedTest
    @CsvSource({"2000-01-01, true", "2026-10-12, true", "2100-12-31, true", "1999-12-31, false", "2101-01-01, false",
            "0001-01-01, false", "+999999999-12-31, false"})
    void onlyDatesFrom2000Through2100AreAccepted(LocalDate date, boolean valid) {
        // Arrange
        // (the date)

        // Act
        boolean accepted = validator.isValid(date, null);

        // Assert
        assertThat(accepted).isEqualTo(valid);
    }

    @Test
    void nothingIsLeftToTheOtherConstraints() {
        // Arrange
        // (no date)

        // Act
        boolean accepted = validator.isValid(null, null);

        // Assert
        assertThat(accepted).isTrue();
    }
}
