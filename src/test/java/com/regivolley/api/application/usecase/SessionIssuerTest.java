package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.identity.RefreshTokenPolicy;
import com.regivolley.api.application.result.SessionTokens;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Threat model D-7: opening a session stores only the hash of an opaque token and starts a family with both lifetimes. */
class SessionIssuerTest {

    private final SessionTestSupport t = new SessionTestSupport();

    @Test
    void opensASessionWithAnAccessTokenAndAnOpaqueRefreshTokenStoredOnlyAsAHash() {
        // Arrange
        // (the confirmed user of the support class)

        // Act
        SessionTokens opened = t.open();

        // Assert
        assertThat(opened.accessToken()).isNotBlank();
        assertThat(opened.expiresInSeconds()).isEqualTo(600);
        assertThat(opened.refreshToken()).hasSize(43);
        RefreshToken first = t.stored(opened.refreshToken());
        assertThat(first.tokenHash()).isEqualTo(t.h.secrets.hash(opened.refreshToken())).isNotEqualTo(opened.refreshToken());
        assertThat(first.parentId()).isNull();
        assertThat(first.userId()).isEqualTo(t.user.id());
        assertThat(first.membershipId()).isEqualTo(t.membership.id());
        assertThat(first.expiresAt()).isEqualTo(CredentialsHarness.NOW.plus(RefreshTokenPolicy.ABSOLUTE_LIFETIME));
        assertThat(first.idleExpiresAt()).isEqualTo(CredentialsHarness.NOW.plus(RefreshTokenPolicy.IDLE_LIFETIME));
        assertThat(opened.refreshExpiresAt()).isEqualTo(first.idleExpiresAt());
    }

    @Test
    void everyLoginStartsItsOwnFamily() {
        // Arrange
        SessionTokens one = t.open();

        // Act
        SessionTokens two = t.open();

        // Assert
        assertThat(t.stored(one.refreshToken()).familyId()).isNotEqualTo(t.stored(two.refreshToken()).familyId());
    }

    @Test
    void openingASessionClearsTheUsersExpiredTokens() {
        // Arrange
        // (the confirmed user of the support class)

        // Act
        t.open();

        // Assert
        verify(t.tokens).deleteExpiredOf(t.user.id(), t.h.clock.instant());
    }
}
