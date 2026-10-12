package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.domain.exception.AttendanceAlreadyMarkedException;
import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.BookingOverlapException;
import com.regivolley.api.domain.exception.BookingWindowClosedException;
import com.regivolley.api.domain.exception.BusinessRuleException;
import com.regivolley.api.domain.exception.CancellationClosedException;
import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.exception.MemberAnonymisedException;
import com.regivolley.api.domain.exception.SessionNotScheduledException;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.infrastructure.web.dto.RefusalReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The stable, machine-readable reason of each booking refusal (issue #57); anything else has none. */
class RefusalReasonWebMapperTest {

    private static final Instant WHEN = Instant.parse("2026-10-20T19:00:00Z");

    @ParameterizedTest
    @EnumSource(BookingRejectionReason.class)
    void everyEligibilityRefusalKeepsItsOwnName(BookingRejectionReason rejection) {
        // Arrange
        BusinessRuleException refusal = new BookingNotAllowedException(rejection);

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).map(Enum::name).hasValue(rejection.name());
    }

    @Test
    void theEligibilityReasonsAreExactlyTheDomainOnes() {
        // Arrange
        var wire = java.util.Arrays.stream(RefusalReason.values()).map(Enum::name).toList();

        // Act + Assert
        assertThat(wire).containsAll(java.util.Arrays.stream(BookingRejectionReason.values()).map(Enum::name).toList());
    }

    @Test
    void aWindowNotYetOpenIsBookingNotOpen() {
        // Arrange
        BusinessRuleException refusal = BookingWindowClosedException.notYetOpen(WHEN);

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).hasValue(RefusalReason.BOOKING_NOT_OPEN);
    }

    @Test
    void aWindowThatClosedIsBookingClosed() {
        // Arrange
        BusinessRuleException refusal = BookingWindowClosedException.alreadyClosed(WHEN);

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).hasValue(RefusalReason.BOOKING_CLOSED);
    }

    @Test
    void anOverlapIsOverlap() {
        // Arrange
        BusinessRuleException refusal = new BookingOverlapException(SessionId.generate(), WHEN);

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).hasValue(RefusalReason.OVERLAP);
    }

    @Test
    void aSecondBookingIsDuplicateBooking() {
        // Arrange
        BusinessRuleException refusal = new DuplicateBookingException();

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).hasValue(RefusalReason.DUPLICATE_BOOKING);
    }

    @Test
    void theSessionsCoachBookingItIsCoachOfSession() {
        // Arrange
        BusinessRuleException refusal = new CoachCannotBookOwnSessionException();

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).hasValue(RefusalReason.COACH_OF_SESSION);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"COMPLETED", "CANCELLED"})
    void aSessionThatIsNoLongerScheduledIsSessionNotScheduled(SessionStatus status) {
        // Arrange
        BusinessRuleException refusal = new SessionNotScheduledException(status);

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).hasValue(RefusalReason.SESSION_NOT_SCHEDULED);
    }

    @Test
    void cancellingAfterTheStartIsCancellationClosed() {
        // Arrange
        BusinessRuleException refusal = new CancellationClosedException(WHEN);

        // Act
        Optional<RefusalReason> reason = RefusalReasonWebMapper.of(refusal);

        // Assert
        assertThat(reason).hasValue(RefusalReason.CANCELLATION_CLOSED);
    }

    @Test
    void ruleViolationsOutsideBookingHaveNoReason() {
        // Arrange
        BusinessRuleException[] others = {new LastAdministratorException(), new MemberAnonymisedException(),
                new AttendanceAlreadyMarkedException(), new InvalidFieldException("email", "The email address is not valid")};

        // Act + Assert
        for (BusinessRuleException other : others) {
            assertThat(RefusalReasonWebMapper.of(other)).as(other.getClass().getSimpleName()).isEmpty();
        }
    }

    @Test
    void everyWireReasonIsProducedBySomeRefusal() {
        // Arrange
        BusinessRuleException[] refusals = {
                new BookingNotAllowedException(BookingRejectionReason.MEMBER_INACTIVE),
                new BookingNotAllowedException(BookingRejectionReason.LEVEL_NOT_ALLOWED),
                new BookingNotAllowedException(BookingRejectionReason.PLAN_LEVEL_NOT_ALLOWED),
                new BookingNotAllowedException(BookingRejectionReason.NO_VALID_SUBSCRIPTION),
                new BookingNotAllowedException(BookingRejectionReason.NO_BALANCE),
                new BookingNotAllowedException(BookingRejectionReason.PAYMENT_OVERDUE),
                BookingWindowClosedException.notYetOpen(WHEN), BookingWindowClosedException.alreadyClosed(WHEN),
                new BookingOverlapException(SessionId.generate(), WHEN), new DuplicateBookingException(),
                new CoachCannotBookOwnSessionException(), new SessionNotScheduledException(SessionStatus.CANCELLED),
                new CancellationClosedException(WHEN)};

        // Act
        var produced = java.util.Arrays.stream(refusals).map(RefusalReasonWebMapper::of).flatMap(Optional::stream).toList();

        // Assert - a reason nobody can produce would be documented in the contract for nothing
        assertThat(produced).containsExactlyInAnyOrder(RefusalReason.values());
    }
}
