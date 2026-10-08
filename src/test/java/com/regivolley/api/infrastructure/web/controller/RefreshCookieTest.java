package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.infrastructure.web.controller.RefreshCookie;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void issuedCookieIsHardenedAndLivesUntilTheTokenExpires() {
        // Arrange
        Instant expires = NOW.plus(Duration.ofDays(30));

        // Act
        String cookie = RefreshCookie.issue("token-value", expires, clock);

        // Assert
        assertThat(cookie).startsWith("__Secure-rt=token-value;").contains("Max-Age=2592000").contains("Path=/api/v1/auth")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict").doesNotContain("Domain");
    }

    @Test
    void aTokenAlreadyPastItsEndGetsAZeroLifetimeNotANegativeOne() {
        // Arrange
        Instant expired = NOW.minusSeconds(5);

        // Act
        String cookie = RefreshCookie.issue("token-value", expired, clock);

        // Assert
        assertThat(cookie).contains("Max-Age=0");
    }

    @Test
    void clearingEmptiesTheValueAndExpiresItAtOnceWithTheSameScope() {
        // Arrange
        // (no input)

        // Act
        String cookie = RefreshCookie.clear();

        // Assert
        assertThat(cookie).startsWith("__Secure-rt=;").contains("Max-Age=0").contains("Path=/api/v1/auth").contains("HttpOnly")
                .contains("Secure").contains("SameSite=Strict");
    }
}
