package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Threat model D-9: the per-email limits behind the {@code AttemptThrottle} port. */
class RateLimitingAttemptThrottleTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T09:00:00Z"));
    private final RateLimitingAttemptThrottle throttle = new RateLimitingAttemptThrottle(new RateLimiter(clock));

    @Test
    void theFourthJoinRequestOfTheDayForOneAssociationAndEmailIsRefusedWhateverTheCaseOfTheEmail() {
        // Arrange
        throttle.checkJoinRequest("club-one", "rita@example.com");
        throttle.checkJoinRequest("club-one", "RITA@example.com");
        throttle.checkJoinRequest("club-one", " rita@example.com ");
        Executable act = () -> throttle.checkJoinRequest("club-one", "rita@example.com");

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isBetween(1L, Duration.ofDays(1).toSeconds());
    }

    @Test
    void theSameEmailMayAskAnotherAssociationAndAnotherEmailMayAskTheSame() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            throttle.checkJoinRequest("club-one", "rita@example.com");
        }

        // Act
        throttle.checkJoinRequest("club-two", "rita@example.com");
        throttle.checkJoinRequest("club-one", "ana@example.com");

        // Assert - no exception
        Executable stillRefused = () -> throttle.checkJoinRequest("club-one", "rita@example.com");
        assertThrows(RateLimitExceededException.class, stillRefused);
    }

    @Test
    void theBucketRefillsAfterADay() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            throttle.checkJoinRequest("club-one", "rita@example.com");
        }
        clock.advance(Duration.ofDays(1).plusSeconds(1));

        // Act
        throttle.checkJoinRequest("club-one", "rita@example.com");

        // Assert - no exception: a token is back
        assertThat(clock.instant()).isAfter(Instant.parse("2026-10-13T09:00:00Z"));
    }
}
