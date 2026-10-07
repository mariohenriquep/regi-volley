package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "volley-porto", "club-2026", "a1b", "a-b-c"})
    void acceptsLowercaseLettersDigitsAndSingleHyphens(String raw) {
        // Arrange
        // (raw value from the source)

        // Act
        ShortName shortName = ShortName.of(raw);

        // Assert
        assertThat(shortName.value()).isEqualTo(raw);
        assertThat(shortName).hasToString(raw);
    }

    @Test
    void trimsAndLowercasesBeforeValidating() {
        // Arrange
        String raw = "  Volley-Porto ";

        // Act
        ShortName shortName = ShortName.of(raw);

        // Assert
        assertThat(shortName.value()).isEqualTo("volley-porto");
    }

    @Test
    void acceptsTheLengthBounds() {
        // Arrange
        String min = "a".repeat(ShortName.MIN_LENGTH);
        String max = "a".repeat(ShortName.MAX_LENGTH);

        // Act
        ShortName shortest = ShortName.of(min);
        ShortName longest = ShortName.of(max);

        // Assert
        assertThat(shortest.value()).hasSize(ShortName.MIN_LENGTH);
        assertThat(longest.value()).hasSize(ShortName.MAX_LENGTH);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "ab", "-abc", "abc-", "ab--cd", "ab cd", "ab_cd", "abç", "ab/cd", "a.b.c"})
    void rejectsMalformedValues(String raw) {
        // Arrange
        Executable act = () -> ShortName.of(raw);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("short name");
    }

    @Test
    void rejectsATooLongValue() {
        // Arrange
        String tooLong = "a".repeat(ShortName.MAX_LENGTH + 1);
        Executable act = () -> ShortName.of(tooLong);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("3 to 40");
    }

    @Test
    void theCanonicalConstructorDoesNotNormalise() {
        // Arrange
        Executable act = () -> new ShortName("Upper");

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("short name");
    }
}
