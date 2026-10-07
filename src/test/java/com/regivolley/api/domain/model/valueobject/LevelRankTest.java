package com.regivolley.api.domain.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LevelRankTest {

    private static LevelRank rank(int rank) {
        return new LevelRank(LevelId.generate(), rank);
    }

    @ParameterizedTest
    @CsvSource({"0, 0, true", "2, 1, true", "1, 2, false"})
    void aLevelIsAtLeastAnotherWhenItsRankIsNotLower(int mine, int other, boolean expected) {
        // Arrange
        LevelRank member = rank(mine);

        // Act
        boolean atLeast = member.isAtLeast(rank(other));

        // Assert
        assertThat(atLeast).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0, false", "1, true", "2, true", "3, true"})
    void aGroupAcceptingIntermediateAndAdvancedIsOpenFromTheLowestAcceptedLevelUp(int memberRank, boolean allowed) {
        // Arrange
        List<LevelRank> accepted = List.of(rank(1), rank(2));

        // Act
        boolean canBook = rank(memberRank).canBookGroupAccepting(accepted);

        // Assert
        assertThat(canBook).isEqualTo(allowed);
    }

    @Test
    void aMemberCannotBookAGroupThatAcceptsNothing() {
        // Arrange
        LevelRank member = rank(5);

        // Act
        boolean canBook = member.canBookGroupAccepting(List.of());

        // Assert
        assertThat(canBook).isFalse();
    }

    @Test
    void rejectsANegativeRank() {
        // Arrange
        Executable act = () -> new LevelRank(LevelId.generate(), -1);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("rank");
    }

    @Test
    void rejectsANullLevelId() {
        // Arrange
        Executable act = () -> new LevelRank(null, 1);

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("levelId");
    }
}
