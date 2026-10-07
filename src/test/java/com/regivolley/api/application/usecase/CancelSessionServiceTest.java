package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CancelSessionCommand;
import com.regivolley.api.application.result.CancelledSession;
import com.regivolley.api.domain.exception.AttendanceAlreadyMarkedException;
import com.regivolley.api.domain.exception.CancellationReasonRequiredException;
import com.regivolley.api.domain.exception.InvalidSessionStatusTransitionException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelSessionServiceTest {

    private static final String REASON = "Venue closed";

    @Mock
    private SessionRepository sessions;
    @Mock
    private MemberRepository members;
    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private Notifier notifier;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member coach;
    private TrainingGroup group;
    private CancelSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        group = Data.group(association, coach, "Beginner");
        useCase = new CancelSessionService(sessions, members, subscriptions, transactions, notifier);
        lenient().when(sessions.save(any(Session.class))).thenAnswer(returnsFirstArg());
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
        knows(coach);
    }

    private void knows(Member person) {
        lenient().when(members.findById(association.id(), person.id())).thenReturn(Optional.of(person));
    }

    private void stored(Session session) {
        lenient().when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
    }

    /** The member's pack, already charged for their booking in the session. */
    private Subscription charged(Session session, Member member) {
        Subscription charged = Data.charged(Data.pack(association, member, 10), session, Data.bookingOf(session, member));
        lenient().when(subscriptions.findByMember(association.id(), member.id())).thenReturn(List.of(charged));
        return charged;
    }

    private CancelSessionCommand cancel(Member actor, Session session, String reason) {
        return new CancelSessionCommand(Data.actor(actor), session.id(), reason);
    }

    @Test
    void theCoachCancelsTheSessionRefundsConfirmedCreditsAndNotifiesEveryone() {
        // Arrange
        Member confirmed = Data.member(association);
        Member waitlisted = Data.member(association);
        Session session = Data.booked(Data.booked(Data.session(group, 1), confirmed, Data.NOW.minusSeconds(60)),
                waitlisted, Data.NOW.minusSeconds(30));
        stored(session);
        Subscription confirmedPack = charged(session, confirmed);

        // Act
        CancelledSession result = useCase.execute(cancel(coach, session, REASON));

        // Assert
        assertThat(result.session().status()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(result.session().cancellationReason()).hasValue(REASON);
        assertThat(result.cancelledBookings()).extracting(Booking::memberId)
                .containsExactlyInAnyOrder(confirmed.id(), waitlisted.id());
        assertThat(result.creditsRefunded()).isEqualTo(1);
        ArgumentCaptor<Subscription> refunded = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(refunded.capture());
        assertThat(refunded.getValue().id()).isEqualTo(confirmedPack.id());
        assertThat(refunded.getValue().usages()).isEmpty();
        verify(notifier).sessionCancelled(association.id(), session.id(), confirmed.id(), REASON);
        verify(notifier).sessionCancelled(association.id(), session.id(), waitlisted.id(), REASON);
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void aBookingCancelledLateEarlierStaysUsedAndIsNotNotified() {
        // Arrange
        Member late = Data.member(association);
        Session session = Data.session(group, Data.NOW.plus(Duration.ofHours(3)), 2);
        session = Data.booked(session, late, Data.NOW.minus(Duration.ofDays(1)));
        Booking booking = Data.bookingOf(session, late);
        session = session.cancelBooking(booking.id(), Data.POLICY, Data.CLOCK, member -> false).session();
        stored(session);

        // Act
        CancelledSession result = useCase.execute(cancel(coach, session, REASON));

        // Assert
        assertThat(result.creditsRefunded()).isZero();
        assertThat(result.cancelledBookings()).isEmpty();
        verify(subscriptions, never()).save(any(Subscription.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void aConfirmedBookingWhoseCreditCannotBeFoundIsStillCancelledAndNotifiedButRefundsNothing() {
        // Arrange
        Member confirmed = Data.member(association);
        Session session = Data.booked(Data.session(group, 2), confirmed, Data.NOW.minusSeconds(60));
        stored(session);
        lenient().when(subscriptions.findByMember(association.id(), confirmed.id())).thenReturn(List.of());

        // Act
        CancelledSession result = useCase.execute(cancel(coach, session, REASON));

        // Assert
        assertThat(result.cancelledBookings()).hasSize(1);
        assertThat(result.creditsRefunded()).isZero();
        verify(subscriptions, never()).save(any(Subscription.class));
        verify(notifier).sessionCancelled(association.id(), session.id(), confirmed.id(), REASON);
    }

    @Test
    void anAdministratorMayCancelAnySession() {
        // Arrange
        Member admin = Data.admin(association);
        knows(admin);
        Session session = Data.session(group, 2);
        stored(session);

        // Act
        CancelledSession result = useCase.execute(cancel(admin, session, REASON));

        // Assert
        assertThat(result.session().status()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(result.cancelledBookings()).isEmpty();
    }

    @Test
    void aPlainMemberAndAnotherSessionsCoachMayNot() {
        // Arrange
        Member member = Data.member(association);
        Member otherCoach = Data.coach(association);
        knows(member);
        knows(otherCoach);
        Session session = Data.session(group, 2);
        stored(session);
        Executable byMember = () -> useCase.execute(cancel(member, session, REASON));
        Executable byOtherCoach = () -> useCase.execute(cancel(otherCoach, session, REASON));

        // Act
        NotAllowedException first = assertThrows(NotAllowedException.class, byMember);
        NotAllowedException second = assertThrows(NotAllowedException.class, byOtherCoach);

        // Assert
        assertThat(first.getMessage()).contains("cancel this session");
        assertThat(second).isNotNull();
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void aReasonIsRequired() {
        // Arrange
        Session session = Data.session(group, 2);
        stored(session);
        Executable act = () -> useCase.execute(cancel(coach, session, " "));

        // Act
        CancellationReasonRequiredException ex = assertThrows(CancellationReasonRequiredException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void cannotCancelOnceAttendanceWasMarked() {
        // Arrange
        Member player = Data.member(association);
        Session started = Data.session(group, Data.NOW.minusSeconds(600), 2);
        Session booked = Data.booked(started, player, Data.NOW.minus(Duration.ofDays(1)));
        Session marked = booked.markAttended(Data.bookingOf(booked, player).id(), Data.CLOCK);
        stored(marked);
        Executable act = () -> useCase.execute(cancel(coach, marked, REASON));

        // Act
        AttendanceAlreadyMarkedException ex = assertThrows(AttendanceAlreadyMarkedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void cannotCancelASessionTwice() {
        // Arrange
        Session session = Data.session(group, 2).cancel(REASON);
        stored(session);
        Executable act = () -> useCase.execute(cancel(coach, session, REASON));

        // Act
        InvalidSessionStatusTransitionException ex = assertThrows(InvalidSessionStatusTransitionException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void anUnknownSessionIsNotFound() {
        // Arrange
        Session session = Data.session(group, 2);
        Executable act = () -> useCase.execute(cancel(coach, session, REASON));

        // Act
        SessionNotFoundException ex = assertThrows(SessionNotFoundException.class, act);

        // Assert
        assertThat(ex.sessionId()).isEqualTo(session.id());
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void retriesALostRaceAndNotifiesOnlyOnce() {
        // Arrange
        Member confirmed = Data.member(association);
        Session session = Data.booked(Data.session(group, 2), confirmed, Data.NOW.minusSeconds(60));
        stored(session);
        charged(session, confirmed);
        when(sessions.save(any(Session.class)))
                .thenThrow(new SessionModifiedConcurrentlyException(session.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        CancelledSession result = useCase.execute(cancel(coach, session, REASON));

        // Assert
        assertThat(result.creditsRefunded()).isEqualTo(1);
        assertThat(transactions.opened()).isEqualTo(2);
        verify(notifier, times(1)).sessionCancelled(association.id(), session.id(), confirmed.id(), REASON);
    }

    @Test
    void givesUpWhenEveryAttemptLosesTheRace() {
        // Arrange
        Session session = Data.session(group, 2);
        stored(session);
        when(sessions.save(any(Session.class))).thenThrow(new SessionModifiedConcurrentlyException(session.id()));
        Executable act = () -> useCase.execute(cancel(coach, session, REASON));

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertThat(transactions.opened()).isEqualTo(UnitOfWork.MAX_ATTEMPTS);
        verifyNoInteractions(notifier);
    }
}
