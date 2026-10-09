package com.regivolley.api.application.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountLinkTest {

    private static final Instant EXPIRES = Instant.parse("2026-10-19T09:00:00Z");

    @Test
    void keepsTheReferenceTheTokenAndTheExpiry() {
        // Arrange
        UUID reference = UUID.randomUUID();

        // Act
        AccountLink link = new AccountLink(reference, "secret-token-value", EXPIRES);

        // Assert
        assertThat(link.reference()).isEqualTo(reference);
        assertThat(link.token()).isEqualTo("secret-token-value");
        assertThat(link.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    void toStringNeverPrintsTheToken() {
        // Arrange
        UUID reference = UUID.randomUUID();
        AccountLink link = new AccountLink(reference, "secret-token-value", EXPIRES);

        // Act
        String text = link.toString();

        // Assert
        assertThat(text).contains(reference.toString()).doesNotContain("secret-token-value");
    }

    @Test
    void aBlankTokenIsRefused() {
        // Arrange
        Executable act = () -> new AccountLink(UUID.randomUUID(), " ", EXPIRES);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("blank");
    }

    @Test
    void thePurposesAreActivationAndReset() {
        // Arrange
        // (the enum)

        // Act
        AccountLinkPurpose[] purposes = AccountLinkPurpose.values();

        // Assert
        assertThat(purposes).containsExactly(AccountLinkPurpose.ACTIVATION, AccountLinkPurpose.PASSWORD_RESET);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void aMissingReferenceTokenOrExpiryIsRefused(int missing) {
        // Arrange
        Executable act = () -> new AccountLink(missing == 0 ? null : UUID.randomUUID(), missing == 1 ? null : "t", missing == 2 ? null : EXPIRES);

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }
}
