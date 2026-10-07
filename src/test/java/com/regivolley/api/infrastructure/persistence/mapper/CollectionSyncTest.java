package com.regivolley.api.infrastructure.persistence.mapper;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionSyncTest {

    @Test
    void aSetKeepsWantedRowsAddsNewOnesAndDropsTheRest() {
        // Arrange
        Set<String> target = new HashSet<>(Set.of("keep", "drop"));

        // Act
        CollectionSync.replace(target, Set.of("keep", "add"));

        // Assert
        assertThat(target).containsExactlyInAnyOrder("keep", "add");
    }

    @Test
    void aSetWithTheSameContentIsLeftAsItIs() {
        // Arrange
        Set<String> target = new HashSet<>(Set.of("a", "b"));

        // Act
        CollectionSync.replace(target, Set.of("a", "b"));

        // Assert
        assertThat(target).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void aListWithDifferentContentOrOrderIsReplaced() {
        // Arrange
        List<String> target = new ArrayList<>(List.of("a", "b", "c"));

        // Act
        CollectionSync.replace(target, List.of("c", "a"));

        // Assert
        assertThat(target).containsExactly("c", "a");
    }

    @Test
    void aListWithTheSameContentIsNotTouched() {
        // Arrange
        List<String> target = new ArrayList<>(List.of("a", "b")) {
            @Override
            public void clear() {
                throw new AssertionError("an unchanged list must not be cleared (it would look dirty to Hibernate)");
            }
        };

        // Act
        CollectionSync.replace(target, List.of("a", "b"));

        // Assert
        assertThat(target).containsExactly("a", "b");
    }
}
