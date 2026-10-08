package com.regivolley.api.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BundledCommonPasswordListTest {

    @Test
    void theBundledListLoadsAndHoldsTheUsualSuspects() {
        // Arrange
        BundledCommonPasswordList bundled = BundledCommonPasswordList.load();

        // Act
        boolean common = bundled.contains("password123") && bundled.contains("1234567890");

        // Assert
        assertThat(common).isTrue();
        assertThat(bundled.contains("correct-horse-battery-staple-9x")).isFalse();
        assertThat(bundled.size()).isGreaterThan(100);
    }

    @Test
    void entriesAreComparedInLowerCaseAfterUnicodeNormalisation() {
        // Arrange
        BundledCommonPasswordList list = new BundledCommonPasswordList(Set.of("Password123", "１２３４５６７８９０"));

        // Act
        boolean upper = list.contains("PASSWORD123");
        boolean ascii = list.contains("1234567890");

        // Assert
        assertThat(upper).isTrue();
        assertThat(ascii).isTrue();
    }
}
