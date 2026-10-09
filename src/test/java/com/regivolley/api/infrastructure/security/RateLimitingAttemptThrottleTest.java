package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.testsupport.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;

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

    @Test
    void theFourthRegistrationOfTheDayForOneFoundersEmailIsRefusedWhateverTheCaseOfTheEmail() {
        // Arrange
        throttle.checkRegistration("ana@example.com");
        throttle.checkRegistration("ANA@example.com");
        throttle.checkRegistration(" ana@example.com ");
        Executable act = () -> throttle.checkRegistration("ana@example.com");

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isBetween(1L, Duration.ofDays(1).toSeconds());
    }

    @Test
    void registrationsOfOtherEmailsAreCountedApartAndTheBucketRefillsAfterADay() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            throttle.checkRegistration("ana@example.com");
        }
        throttle.checkRegistration("rita@example.com");
        clock.advance(Duration.ofDays(1).plusSeconds(1));

        // Act
        throttle.checkRegistration("ana@example.com");

        // Assert - no exception: another email was never affected, and a token is back
        Executable stillCounted = () -> {
            throttle.checkRegistration("ana@example.com");
            throttle.checkRegistration("ana@example.com");
            throttle.checkRegistration("ana@example.com");
        };
        assertThrows(RateLimitExceededException.class, stillCounted);
    }

    @Test
    void theRegistrationBudgetIsNotTheResetBudgetOfTheSameEmail() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            throttle.checkRegistration("ana@example.com");
        }

        // Act
        throttle.checkPasswordResetRequest("ana@example.com");

        // Assert - no exception: the keys are hashed per rule, in separate caches
        Executable registrationStillRefused = () -> throttle.checkRegistration("ana@example.com");
        assertThrows(RateLimitExceededException.class, registrationStillRefused);
    }

    @Test
    void theFourthResendOfOneMembersActivationLinkInAnHourIsRefused() {
        // Arrange
        AssociationId association = AssociationId.generate();
        MemberId member = MemberId.generate();
        for (int i = 0; i < 3; i++) {
            throttle.checkActivationLinkResend(association, member);
        }
        Executable act = () -> throttle.checkActivationLinkResend(association, member);

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isBetween(1L, Duration.ofHours(1).toSeconds());
    }

    @Test
    void anotherMemberAndTheSameIdInAnotherAssociationHaveBucketsOfTheirOwnAndTheBucketRefillsAfterAnHour() {
        // Arrange
        AssociationId association = AssociationId.generate();
        MemberId member = MemberId.generate();
        for (int i = 0; i < 3; i++) {
            throttle.checkActivationLinkResend(association, member);
        }

        // Act
        throttle.checkActivationLinkResend(association, MemberId.generate());
        throttle.checkActivationLinkResend(AssociationId.generate(), member);
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        throttle.checkActivationLinkResend(association, member);

        // Assert - no exception: only the exhausted key was refused, and a token is back after the hour
        assertThat(clock.instant()).isAfter(Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    void theThirtyFirstResendInAnHourForOneAssociationIsRefusedWhicheverMembersItIsFor() {
        // Arrange
        AssociationId association = AssociationId.generate();
        for (int i = 0; i < 30; i++) {
            throttle.checkActivationLinkResend(association, MemberId.generate());
        }
        Executable act = () -> throttle.checkActivationLinkResend(association, MemberId.generate());

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isBetween(1L, Duration.ofHours(1).toSeconds());
    }

    @Test
    void theAssociationCapDoesNotTouchAnotherAssociationAndRefillsAfterAnHour() {
        // Arrange
        AssociationId association = AssociationId.generate();
        for (int i = 0; i < 30; i++) {
            throttle.checkActivationLinkResend(association, MemberId.generate());
        }

        // Act
        throttle.checkActivationLinkResend(AssociationId.generate(), MemberId.generate());
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        throttle.checkActivationLinkResend(association, MemberId.generate());

        // Assert - no exception
        assertThat(clock.instant()).isAfter(Instant.parse("2026-10-12T10:00:00Z"));
    }

    @Test
    void theSeventhLinkMailToOneAddressInAnHourIsRefusedWhateverTheCaseOfTheAddress() {
        // Arrange
        for (int i = 0; i < 6; i++) {
            throttle.checkLinkMailAddress(EmailAddress.of(i % 2 == 0 ? "rita@example.com" : "RITA@example.com"));
        }
        Executable act = () -> throttle.checkLinkMailAddress(EmailAddress.of("rita@example.com"));

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isBetween(1L, Duration.ofHours(1).toSeconds());
    }

    @Test
    void anotherAddressHasABucketOfItsOwnAndTheBucketRefillsAfterAnHour() {
        // Arrange
        for (int i = 0; i < 6; i++) {
            throttle.checkLinkMailAddress(EmailAddress.of("rita@example.com"));
        }

        // Act
        throttle.checkLinkMailAddress(EmailAddress.of("ana@example.com"));
        clock.advance(Duration.ofHours(1).plusSeconds(1));
        throttle.checkLinkMailAddress(EmailAddress.of("rita@example.com"));

        // Assert - no exception
        assertThat(clock.instant()).isAfter(Instant.parse("2026-10-12T10:00:00Z"));
    }
}
