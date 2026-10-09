package com.regivolley.api.application.usecase;

import com.regivolley.api.application.exception.InvalidRefreshTokenException;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.identity.RefreshTokenPolicy;
import com.regivolley.api.application.result.SessionTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/** Threat model D-7: rotation on every use, reuse detection with a grace window, idle and absolute lifetimes, revocation. */
class RefreshSessionServiceTest {

    private final SessionTestSupport t = new SessionTestSupport();

    private Executable refreshing(String raw) {
        return () -> t.refresh(raw);
    }

    @Test
    void refreshRotatesTheTokenInTheSameFamilyAndInvalidatesTheOldOne() {
        // Arrange
        SessionTokens opened = t.open();
        t.h.clock.advance(Duration.ofMinutes(5));

        // Act
        SessionTokens rotated = t.refresh(opened.refreshToken());

        // Assert
        assertThat(rotated.refreshToken()).isNotEqualTo(opened.refreshToken());
        RefreshToken old = t.stored(opened.refreshToken());
        RefreshToken successor = t.stored(rotated.refreshToken());
        assertThat(old.usedAt()).isEqualTo(t.h.clock.instant());
        assertThat(successor.familyId()).isEqualTo(old.familyId());
        assertThat(successor.parentId()).isEqualTo(old.id());
        assertThat(successor.expiresAt()).isEqualTo(old.expiresAt());
        assertThat(successor.idleExpiresAt()).isEqualTo(t.h.clock.instant().plus(RefreshTokenPolicy.IDLE_LIFETIME));
        assertThat(rotated.accessToken()).isNotBlank();
    }

    @Test
    void anUnknownTokenIsRefusedWithoutEchoingIt() {
        // Arrange
        String unknown = t.h.secrets.newSecret();
        Executable act = refreshing(unknown);

        // Act
        InvalidRefreshTokenException ex = assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain(unknown);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aBlankOrMissingTokenIsRefused(String raw) {
        // Arrange
        Executable act = refreshing(raw);

        // Act
        InvalidRefreshTokenException ex = assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void reusingARotatedTokenAfterTheGraceWindowRevokesTheWholeFamily() {
        // Arrange
        SessionTokens opened = t.open();
        SessionTokens rotated = t.refresh(opened.refreshToken());
        t.h.clock.advance(RefreshTokenPolicy.REUSE_GRACE.plusSeconds(1));
        Executable replay = refreshing(opened.refreshToken());

        // Act
        assertThrows(InvalidRefreshTokenException.class, replay);

        // Assert - the thief's replay also killed the legitimate newest token
        assertThat(t.stored(rotated.refreshToken()).isRevoked()).isTrue();
        assertThat(t.stored(opened.refreshToken()).isRevoked()).isTrue();
    }

    @Test
    void aSecondPresentationWithinTheGraceWindowIsRefusedButTheFamilyLives() {
        // Arrange - two tabs refresh with the same cookie a moment apart
        SessionTokens opened = t.open();
        SessionTokens rotated = t.refresh(opened.refreshToken());
        t.h.clock.advance(RefreshTokenPolicy.REUSE_GRACE.minusSeconds(1));
        Executable duplicate = refreshing(opened.refreshToken());

        // Act
        assertThrows(InvalidRefreshTokenException.class, duplicate);

        // Assert
        assertThat(t.stored(rotated.refreshToken()).isRevoked()).isFalse();
        assertThat(t.refresh(rotated.refreshToken()).refreshToken()).isNotBlank();
    }

    @Test
    void losingTheRaceToRotateIsTreatedLikeTheGraceWindowAndRevokesNothing() {
        // Arrange - another request marked the token used between our read and our update
        SessionTokens opened = t.open();
        doReturn(false).when(t.tokens).markUsed(any(), any());
        Executable act = refreshing(opened.refreshToken());

        // Act
        assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(t.stored(opened.refreshToken()).isRevoked()).isFalse();
        assertThat(t.stored(opened.refreshToken()).isUsed()).isFalse();
    }

    @Test
    void aTokenIsDeadAfterThirtyIdleDays() {
        // Arrange
        SessionTokens opened = t.open();
        t.h.clock.advance(RefreshTokenPolicy.IDLE_LIFETIME.plusSeconds(1));
        Executable act = refreshing(opened.refreshToken());

        // Act
        InvalidRefreshTokenException ex = assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void aTokenStillWorksJustBeforeThirtyIdleDays() {
        // Arrange
        SessionTokens opened = t.open();
        t.h.clock.advance(RefreshTokenPolicy.IDLE_LIFETIME.minusSeconds(1));

        // Act
        SessionTokens rotated = t.refresh(opened.refreshToken());

        // Assert
        assertThat(rotated.refreshToken()).isNotBlank();
    }

    @Test
    void aLoginEndsNinetyDaysAfterItStartedNoMatterHowOftenItIsRefreshed() {
        // Arrange - refresh every 20 days: never idle, but the family reaches its absolute end
        SessionTokens current = t.open();
        for (int i = 0; i < 4; i++) {
            t.h.clock.advance(Duration.ofDays(20));
            current = t.refresh(current.refreshToken());
        }
        t.h.clock.advance(Duration.ofDays(11));
        Executable act = refreshing(current.refreshToken());

        // Act
        InvalidRefreshTokenException ex = assertThrows(InvalidRefreshTokenException.class, act);

        // Assert - 91 days after the login
        assertThat(ex).isNotNull();
    }

    @Test
    void theRefreshCookieNeverOutlivesTheAbsoluteEnd() {
        // Arrange
        SessionTokens current = t.open();
        for (int i = 0; i < 4; i++) {
            t.h.clock.advance(Duration.ofDays(20));
            current = t.refresh(current.refreshToken());
        }

        // Act
        Instant end = t.stored(current.refreshToken()).expiresAt();

        // Assert - 80 days in, the idle end (110 days) is capped at the absolute end (90 days)
        assertThat(current.refreshExpiresAt()).isEqualTo(end);
    }

    @Test
    void aDeactivatedMemberCannotRefreshAndTheirFamilyIsRevoked() {
        // Arrange
        SessionTokens opened = t.open();
        when(t.h.members.findById(t.h.association.id(), t.h.member.id())).thenReturn(Optional.of(t.h.member.deactivate()));
        Executable act = refreshing(opened.refreshToken());

        // Act
        assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(t.stored(opened.refreshToken()).isRevoked()).isTrue();
    }

    @Test
    void aDisabledAccountCannotRefresh() {
        // Arrange
        SessionTokens opened = t.open();
        t.h.stores.users.disable(t.user.id());
        Executable act = refreshing(opened.refreshToken());

        // Act
        assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(t.stored(opened.refreshToken()).isRevoked()).isTrue();
    }

    @Test
    void aTokenWhoseAccountWasErasedIsRefusedAndItsFamilyRevoked() {
        // Arrange
        SessionTokens opened = t.open();
        RefreshToken token = t.stored(opened.refreshToken());
        t.h.stores.users.byId.remove(t.user.id());
        Executable act = refreshing(opened.refreshToken());

        // Act
        assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(t.tokens.findByTokenHash(token.tokenHash()).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void aRevokedFamilyStaysRefused() {
        // Arrange
        SessionTokens opened = t.open();
        t.tokens.revokeFamily(t.stored(opened.refreshToken()).familyId(), t.h.clock.instant());
        Executable act = refreshing(opened.refreshToken());

        // Act
        InvalidRefreshTokenException ex = assertThrows(InvalidRefreshTokenException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void theRefreshRunsInOneTransaction() {
        // Arrange
        SessionTokens opened = t.open();

        // Act
        t.refresh(opened.refreshToken());

        // Assert
        assertThat(t.h.transactions.opened()).isEqualTo(1);
    }
}
