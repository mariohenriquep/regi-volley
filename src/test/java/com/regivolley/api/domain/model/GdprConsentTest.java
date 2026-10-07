package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.ConsentRequiredException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GdprConsentTest {

    private static final Instant NOW = Instant.parse("2026-10-07T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void recordsAnAcceptanceWithTheServerTimeAndPolicyVersion() {
        // Arrange
        String version = " 2026-10 ";

        // Act
        GdprConsent consent = GdprConsent.record(true, version, CLOCK);

        // Assert
        assertThat(consent.givenAt()).isEqualTo(NOW);
        assertThat(consent.policyVersion()).isEqualTo("2026-10");
    }

    @Test
    void refusesWhenNotAccepted() {
        // Arrange
        Executable act = () -> GdprConsent.record(false, "2026-10", CLOCK);

        // Act
        ConsentRequiredException ex = assertThrows(ConsentRequiredException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("consent");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void requiresAPolicyVersion(String version) {
        // Arrange
        Executable act = () -> GdprConsent.record(true, version, CLOCK);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("policy version");
    }

    @Test
    void rejectsAPolicyVersionThatIsTooLong() {
        // Arrange
        String version = "v".repeat(GdprConsent.MAX_POLICY_VERSION_LENGTH + 1);
        Executable act = () -> new GdprConsent(NOW, version);

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("policy version");
    }

    @Test
    void requiresATimestamp() {
        // Arrange
        Executable act = () -> new GdprConsent(null, "2026-10");

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("givenAt");
    }
}
