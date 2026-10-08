package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.CancelBookingCommand;
import com.regivolley.api.application.result.CancelledBooking;
import com.regivolley.api.domain.exception.BookingNotFoundException;
import com.regivolley.api.domain.exception.CancellationClosedException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelBookingServiceTest {

    @Mock
    private SessionRepository sessions;
    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private TrainingGroupRepository groups;
    @Mock
    private SubscriptionRepository subscriptions;
    @Mock
    private Notifier notifier;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member coach;
    private Member owner;
    private TrainingGroup group;
    private CancelBookingUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        owner = Data.member(association);
        group = Data.group(association, coach, "Beginner", "Intermediate");
        useCase = new CancelBookingService(sessions, members, associations, groups, subscriptions, transactions,
                notifier, Data.CLOCK);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group));
        lenient().when(sessions.save(any(Session.class))).thenAnswer(returnsFirstArg());
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
        knows(coach, owner);
    }

    private void knows(Member... people) {
        for (Member person : people) {
            lenient().when(members.findById(association.id(), person.id())).thenReturn(Optional.of(person));
        }
    }

    private void stored(Session session) {
        lenient().when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
    }

    private void holds(Member member, Subscription... held) {
        lenient().when(subscriptions.findByMember(association.id(), member.id())).thenReturn(List.of(held));
    }

    private static CancelBookingCommand cancel(Member actor, Session session, Booking booking) {
        return new CancelBookingCommand(Data.actor(actor), session.id(), booking.id());
    }

    /** The owner confirmed in a session, with a pack that already holds the place. */
    private record Booked(Session session, Booking booking, Subscription charged) {
    }

    private Booked ownerConfirmed(Session base) {
        Session session = Data.booked(base, owner);
        Booking booking = Data.bookingOf(session, owner);
        Subscription charged = Data.charged(Data.pack(association, owner, 10), session, booking);
        stored(session);
        holds(owner, charged);
        return new Booked(session, booking, charged);
    }

    /** A session of capacity 1 held by the owner, with {@code waiting} queued behind them. */
    private Session fullWithQueue(Member... waiting) {
        Session session = Data.booked(Data.session(group, 1), owner, Data.NOW.minusSeconds(1_000));
        long offset = 1;
        for (Member member : waiting) {
            session = Data.booked(session, member, Data.NOW.minusSeconds(1_000 - offset++));
        }
        stored(session);
        knows(waiting);
        holds(owner, Data.charged(Data.pack(association, owner, 10), session, Data.bookingOf(session, owner)));
        return session;
    }

    @Test
    void theOwnerCancelsFreeBeforeTheDeadlineAndGetsTheCreditBack() {
        // Arrange
        Booked booked = ownerConfirmed(Data.session(group, 2));

        // Act
        CancelledBooking result = useCase.execute(cancel(owner, booked.session(), booked.booking()));

        // Assert
        assertThat(result.cancelled().status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(result.cancelled().cancellationKind()).hasValue(CancellationKind.FREE);
        assertThat(result.late()).isFalse();
        assertThat(result.creditRefunded()).isTrue();
        assertThat(result.promoted()).isEmpty();
        ArgumentCaptor<Subscription> refunded = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(refunded.capture());
        assertThat(refunded.getValue().holdsPlaceFor(booked.booking().id())).isFalse();
        assertThat(refunded.getValue().id()).isEqualTo(booked.charged().id());
        verifyNoInteractions(notifier);
    }

    @Test
    void aLateCancellationFreesTheSeatButKeepsTheCreditUsed() {
        // Arrange
        Session soon = Data.session(group, Data.NOW.plus(Duration.ofHours(3)), 2);
        Booked booked = ownerConfirmed(soon);

        // Act
        CancelledBooking result = useCase.execute(cancel(owner, booked.session(), booked.booking()));

        // Assert
        assertThat(result.late()).isTrue();
        assertThat(result.creditRefunded()).isFalse();
        assertThat(result.cancelled().cancellationKind()).hasValue(CancellationKind.LATE);
        assertThat(result.session().confirmedCount()).isZero();
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void cancellingAWaitlistedBookingIsFreeAndRefundsNothing() {
        // Arrange
        Member queued = owner;
        Session session = Data.booked(Data.booked(Data.session(group, 1), Data.member(association)), queued);
        Booking waiting = Data.bookingOf(session, queued);
        stored(session);

        // Act
        CancelledBooking result = useCase.execute(cancel(queued, session, waiting));

        // Assert
        assertThat(result.cancelled().cancellationKind()).hasValue(CancellationKind.FREE);
        assertThat(result.creditRefunded()).isFalse();
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void promotesTheFirstEligibleWaitingMemberChargesThemAndNotifiesThem() {
        // Arrange
        Member waiting = Data.member(association);
        Session session = fullWithQueue(waiting);
        Subscription waitingPack = Data.pack(association, waiting, 10);
        holds(waiting, waitingPack);

        // Act
        CancelledBooking result = useCase.execute(cancel(owner, session, Data.bookingOf(session, owner)));

        // Assert
        assertThat(result.promoted()).hasSize(1);
        Booking promoted = result.promoted().get(0);
        assertThat(promoted.memberId()).isEqualTo(waiting.id());
        assertThat(promoted.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.session().confirmedCount()).isEqualTo(1);
        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions, times(2)).save(saved.capture());
        Subscription charged = saved.getAllValues().stream()
                .filter(s -> s.id().equals(waitingPack.id())).findFirst().orElseThrow();
        assertThat(charged.holdsPlaceFor(promoted.id())).isTrue();
        verify(notifier).bookingPromoted(association.id(), session.id(), waiting.id());
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void skipsAWaitingMemberWithoutBalanceAndPromotesTheNextOne() {
        // Arrange
        Member broke = Data.member(association);
        Member next = Data.member(association);
        Session session = fullWithQueue(broke, next);
        holds(broke);
        holds(next, Data.unlimited(association, next));

        // Act
        CancelledBooking result = useCase.execute(cancel(owner, session, Data.bookingOf(session, owner)));

        // Assert
        assertThat(result.promoted()).extracting(Booking::memberId).containsExactly(next.id());
        assertThat(result.session().waitlist()).extracting(Booking::memberId).containsExactly(broke.id());
        verify(notifier).bookingPromoted(association.id(), session.id(), next.id());
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void theCoachOfTheSessionMayCancelAMembersBooking() {
        // Arrange
        Booked booked = ownerConfirmed(Data.session(group, 2));

        // Act
        CancelledBooking result = useCase.execute(cancel(coach, booked.session(), booked.booking()));

        // Assert
        assertThat(result.cancelled().memberId()).isEqualTo(owner.id());
        assertThat(result.creditRefunded()).isTrue();
    }

    @Test
    void anAdministratorMayCancelAMembersBooking() {
        // Arrange
        Member admin = Data.admin(association);
        knows(admin);
        Booked booked = ownerConfirmed(Data.session(group, 2));

        // Act
        CancelledBooking result = useCase.execute(cancel(admin, booked.session(), booked.booking()));

        // Assert
        assertThat(result.cancelled().status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void anotherMemberMayNotCancelSomeoneElsesBooking() {
        // Arrange
        Member stranger = Data.member(association);
        knows(stranger);
        Booked booked = ownerConfirmed(Data.session(group, 2));
        Executable act = () -> useCase.execute(cancel(stranger, booked.session(), booked.booking()));

        // Act
        NotAllowedException ex = assertThrows(NotAllowedException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("cancel another member's booking");
        verify(sessions, never()).save(any(Session.class));
        verify(subscriptions, never()).save(any(Subscription.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void aCoachOfAnotherSessionMayNotCancelEither() {
        // Arrange
        Member otherCoach = Data.coach(association);
        knows(otherCoach);
        Booked booked = ownerConfirmed(Data.session(group, 2));
        Executable act = () -> useCase.execute(cancel(otherCoach, booked.session(), booked.booking()));

        // Act
        NotAllowedException ex = assertThrows(NotAllowedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void aDeactivatedAdministratorHoldsNoPowerAnymore() {
        // Arrange
        Member former = Data.member(association, MemberRole.ADMIN).deactivate();
        knows(former);
        Booked booked = ownerConfirmed(Data.session(group, 2));
        Executable act = () -> useCase.execute(cancel(former, booked.session(), booked.booking()));

        // Act
        NotAllowedException ex = assertThrows(NotAllowedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void failsForABookingThatIsNotInTheSessionOrAnUnknownSession() {
        // Arrange
        Booked booked = ownerConfirmed(Data.session(group, 2));
        Executable noBooking = () -> useCase.execute(
                new CancelBookingCommand(Data.actor(owner), booked.session().id(), BookingId.generate()));
        Executable noSession = () -> useCase.execute(new CancelBookingCommand(Data.actor(owner),
                Data.session(group, 2).id(), booked.booking().id()));

        // Act
        BookingNotFoundException missingBooking = assertThrows(BookingNotFoundException.class, noBooking);
        SessionNotFoundException missingSession = assertThrows(SessionNotFoundException.class, noSession);

        // Assert
        assertThat(missingBooking).isNotNull();
        assertThat(missingSession).isNotNull();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void cannotCancelOnceTheSessionHasStarted() {
        // Arrange
        Session started = Data.session(group, Data.NOW.minusSeconds(60), 2);
        Session session = Data.booked(started, owner, Data.NOW.minus(Duration.ofDays(1)));
        Booking booking = Data.bookingOf(session, owner);
        stored(session);
        Executable act = () -> useCase.execute(cancel(owner, session, booking));

        // Act
        CancellationClosedException ex = assertThrows(CancellationClosedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void retriesALostRaceAndNotifiesThePromotedMemberOnlyOnce() {
        // Arrange
        Member waiting = Data.member(association);
        Session session = fullWithQueue(waiting);
        holds(waiting, Data.unlimited(association, waiting));
        when(sessions.save(any(Session.class)))
                .thenThrow(new SessionModifiedConcurrentlyException(session.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        CancelledBooking result = useCase.execute(cancel(owner, session, Data.bookingOf(session, owner)));

        // Assert
        assertThat(result.promoted()).hasSize(1);
        assertThat(transactions.opened()).isEqualTo(2);
        verify(notifier, times(1)).bookingPromoted(association.id(), session.id(), waiting.id());
    }

    @Test
    void givesUpWithTheConflictAndNeverNotifiesWhenEveryAttemptLoses() {
        // Arrange
        Booked booked = ownerConfirmed(Data.session(group, 2));
        when(sessions.save(any(Session.class))).thenThrow(new SessionModifiedConcurrentlyException(booked.session().id()));
        Executable act = () -> useCase.execute(cancel(owner, booked.session(), booked.booking()));

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertThat(transactions.opened()).isEqualTo(UnitOfWork.MAX_ATTEMPTS);
        verify(subscriptions, never()).save(any(Subscription.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void aFailingNotifierDoesNotUndoTheCancellation() {
        // Arrange
        Member waiting = Data.member(association);
        Session session = fullWithQueue(waiting);
        holds(waiting, Data.unlimited(association, waiting));
        doThrow(new IllegalStateException("mail down")).when(notifier)
                .bookingPromoted(association.id(), session.id(), waiting.id());

        // Act
        CancelledBooking result = useCase.execute(cancel(owner, session, Data.bookingOf(session, owner)));

        // Assert
        assertThat(result.promoted()).hasSize(1);
        verify(sessions).save(any(Session.class));
    }

    @Test
    void anActorOfAnotherAssociationFindsNothing() {
        // Arrange
        Association other = Data.association();
        Member outsider = Data.member(other);
        when(members.findById(other.id(), outsider.id())).thenReturn(Optional.of(outsider));
        Booked booked = ownerConfirmed(Data.session(group, 2));
        Executable act = () -> useCase.execute(new CancelBookingCommand(new Actor(other.id(), outsider.id()),
                booked.session().id(), booked.booking().id()));

        // Act
        SessionNotFoundException ex = assertThrows(SessionNotFoundException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions).findById(other.id(), booked.session().id());
        verify(sessions, never()).save(any(Session.class));
    }
}
