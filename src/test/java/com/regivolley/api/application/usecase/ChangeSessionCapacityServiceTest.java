package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeSessionCapacityCommand;
import com.regivolley.api.application.result.CapacityChanged;
import com.regivolley.api.domain.exception.CapacityBelowConfirmedException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionAlreadyStartedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeSessionCapacityServiceTest {

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
    private TrainingGroup group;
    private ChangeSessionCapacityUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        group = Data.group(association, coach, "Beginner");
        useCase = new ChangeSessionCapacityService(sessions, members, associations, groups, subscriptions,
                transactions, notifier, Data.CLOCK);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group));
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

    private Subscription holds(Member member, Subscription... held) {
        lenient().when(subscriptions.findByMember(association.id(), member.id())).thenReturn(List.of(held));
        return held.length == 0 ? null : held[0];
    }

    /** A full session of capacity 1 with the given members queued behind the seat holder, oldest first. */
    private Session fullWithQueue(Member... waiting) {
        Session session = Data.booked(Data.session(group, 1), Data.member(association), Data.NOW.minusSeconds(1_000));
        long offset = 1;
        for (Member member : waiting) {
            session = Data.booked(session, member, Data.NOW.minusSeconds(1_000 - offset++));
            knows(member);
        }
        stored(session);
        return session;
    }

    private ChangeSessionCapacityCommand change(Member actor, Session session, int capacity) {
        return new ChangeSessionCapacityCommand(Data.actor(actor), session.id(), capacity);
    }

    @Test
    void raisingTheCapacityPromotesTheQueueInOrderChargesAndNotifies() {
        // Arrange
        Member first = Data.member(association);
        Member second = Data.member(association);
        Member third = Data.member(association);
        Session session = fullWithQueue(first, second, third);
        Subscription firstPack = holds(first, Data.pack(association, first, 10));
        holds(second, Data.unlimited(association, second));
        holds(third, Data.unlimited(association, third));

        // Act
        CapacityChanged result = useCase.execute(change(coach, session, 3));

        // Assert
        assertThat(result.session().capacity()).isEqualTo(3);
        assertThat(result.promoted()).extracting(Booking::memberId).containsExactly(first.id(), second.id());
        assertThat(result.session().waitlist()).extracting(Booking::memberId).containsExactly(third.id());
        ArgumentCaptor<Subscription> charged = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions, times(2)).save(charged.capture());
        Subscription firstCharged = charged.getAllValues().stream()
                .filter(s -> s.id().equals(firstPack.id())).findFirst().orElseThrow();
        assertThat(firstCharged.holdsPlaceFor(result.promoted().get(0).id())).isTrue();
        verify(notifier).bookingPromoted(association.id(), session.id(), first.id());
        verify(notifier).bookingPromoted(association.id(), session.id(), second.id());
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void aMemberWithoutBalanceStaysWaitlistedAndIsNotNotified() {
        // Arrange
        Member broke = Data.member(association);
        Session session = fullWithQueue(broke);
        holds(broke);

        // Act
        CapacityChanged result = useCase.execute(change(coach, session, 2));

        // Assert
        assertThat(result.promoted()).isEmpty();
        assertThat(result.session().waitlist()).extracting(Booking::status).containsExactly(BookingStatus.WAITLISTED);
        verify(subscriptions, never()).save(any(Subscription.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void loweringTheCapacityNeedsNoPromotionAndNothingIsCharged() {
        // Arrange
        Session session = Data.session(group, 5);
        stored(session);

        // Act
        CapacityChanged result = useCase.execute(change(coach, session, 3));

        // Assert
        assertThat(result.session().capacity()).isEqualTo(3);
        assertThat(result.promoted()).isEmpty();
        verify(sessions).save(any(Session.class));
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void refusesACapacityOfZero() {
        // Arrange
        Session session = Data.session(group, 3);
        stored(session);
        Executable act = () -> useCase.execute(change(coach, session, 0));

        // Act
        InvalidCapacityException ex = assertThrows(InvalidCapacityException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void refusesACapacityBelowTheConfirmedBookings() {
        // Arrange
        Session session = Data.booked(Data.booked(Data.session(group, 3), Data.member(association), Data.NOW.minusSeconds(10)),
                Data.member(association), Data.NOW.minusSeconds(5));
        stored(session);
        Executable act = () -> useCase.execute(change(coach, session, 1));

        // Act
        CapacityBelowConfirmedException ex = assertThrows(CapacityBelowConfirmedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void refusesAChangeOnceTheSessionHasStarted() {
        // Arrange
        Session session = Data.session(group, Data.NOW.minus(Duration.ofMinutes(5)), 3);
        stored(session);
        Executable act = () -> useCase.execute(change(coach, session, 5));

        // Act
        SessionAlreadyStartedException ex = assertThrows(SessionAlreadyStartedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void anAdministratorMayChangeAnySessionButAPlainMemberOrAnotherCoachMayNot() {
        // Arrange
        Member admin = Data.admin(association);
        Member member = Data.member(association);
        Member otherCoach = Data.coach(association);
        knows(admin);
        knows(member);
        knows(otherCoach);
        Session session = Data.session(group, 3);
        stored(session);
        Executable byMember = () -> useCase.execute(change(member, session, 5));
        Executable byOtherCoach = () -> useCase.execute(change(otherCoach, session, 5));

        // Act
        CapacityChanged byAdmin = useCase.execute(change(admin, session, 5));
        NotAllowedException denied = assertThrows(NotAllowedException.class, byMember);
        NotAllowedException deniedToo = assertThrows(NotAllowedException.class, byOtherCoach);

        // Assert
        assertThat(byAdmin.session().capacity()).isEqualTo(5);
        assertThat(denied.getMessage()).contains("change this session's capacity");
        assertThat(deniedToo).isNotNull();
        verify(sessions, times(1)).save(any(Session.class));
    }

    @Test
    void anUnknownSessionIsNotFound() {
        // Arrange
        Session session = Data.session(group, 3);
        Executable act = () -> useCase.execute(change(coach, session, 5));

        // Act
        SessionNotFoundException ex = assertThrows(SessionNotFoundException.class, act);

        // Assert
        assertThat(ex.sessionId()).isEqualTo(session.id());
    }

    @Test
    void retriesALostRaceAndNotifiesOnlyOnce() {
        // Arrange
        Member waiting = Data.member(association);
        Session session = fullWithQueue(waiting);
        holds(waiting, Data.unlimited(association, waiting));
        when(sessions.save(any(Session.class)))
                .thenThrow(new SessionModifiedConcurrentlyException(session.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        CapacityChanged result = useCase.execute(change(coach, session, 2));

        // Assert
        assertThat(result.promoted()).hasSize(1);
        assertThat(transactions.opened()).isEqualTo(2);
        verify(notifier, times(1)).bookingPromoted(association.id(), session.id(), waiting.id());
    }

    @Test
    void givesUpWhenEveryAttemptLosesTheRace() {
        // Arrange
        Session session = Data.session(group, 3);
        stored(session);
        when(sessions.save(any(Session.class))).thenThrow(new SessionModifiedConcurrentlyException(session.id()));
        Executable act = () -> useCase.execute(change(coach, session, 5));

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertThat(transactions.opened()).isEqualTo(UnitOfWork.MAX_ATTEMPTS);
        verifyNoInteractions(notifier);
    }
}
