package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NifTest {

    @ParameterizedTest
    // 123456789: check digit 9; 999999990 and 100000070: weighted sum gives 11 and 10, so the check digit is 0
    @ValueSource(strings = {"123456789", "999999990", "100000070", "501964843"})
    void acceptsANumberWhoseCheckDigitIsRight(String raw) {
        // Arrange
        // (raw value from the source)

        // Act
        Nif nif = Nif.of(raw);

        // Assert
        assertThat(nif.value()).isEqualTo(raw);
        assertThat(nif).hasToString(raw);
    }

    @Test
    void ignoresSpacesInTheInput() {
        // Arrange
        String raw = " 123 456 789 ";

        // Act
        Nif nif = Nif.of(raw);

        // Assert
        assertThat(nif.value()).isEqualTo("123456789");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123456780", "123456788", "100000071", "12345678", "1234567890", "12345678a", "abcdefghi", "123-456-789"})
    void rejectsAWrongCheckDigitLengthOrCharacters(String raw) {
        // Arrange
        Executable act = () -> Nif.of(raw);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("NIF");
    }
}
