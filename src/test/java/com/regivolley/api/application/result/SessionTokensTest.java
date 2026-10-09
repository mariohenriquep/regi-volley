package com.regivolley.api.application.result;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokensTest {

    @Test
    void neverPrintsTheTokens() {
        // Arrange
        SessionTokens tokens = new SessionTokens("jwt-value", 600, "refresh-value", Instant.parse("2026-10-12T09:00:00Z"));

        // Act
        String text = tokens.toString();

        // Assert
        assertThat(text).doesNotContain("jwt-value").doesNotContain("refresh-value").contains("600");
    }
}
