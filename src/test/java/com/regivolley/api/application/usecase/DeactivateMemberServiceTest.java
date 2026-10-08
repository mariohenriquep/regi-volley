package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.result.MemberDeactivated;
import com.regivolley.api.domain.exception.LastAdministratorException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateMemberServiceTest {

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
    private Member admin;
    private Member coach;
    private Member target;
    private TrainingGroup group;
    private DeactivateMemberUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        admin = Data.admin(association);
        coach = Data.coach(association);
        target = Data.member(association);
        group = Data.group(association, coach, "Beginner");
        useCase = new DeactivateMemberService(sessions, members, associations, groups, subscriptions, transactions,
                notifier, Data.CLOCK);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(associations.findByIdForUpdate(association.id())).thenReturn(Optional.of(association));
        lenient().when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group));
        lenient().when(sessions.save(any(Session.class))).thenAnswer(returnsFirstArg());
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
        lenient().when(members.save(any(Member.class))).thenAnswer(returnsFirstArg());
        knows(admin, coach, target);
        lenient().when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of());
    }

    private void knows(Member... people) {
        for (Member person : people) {
            lenient().when(members.findById(association.id(), person.id())).thenReturn(Optional.of(person));
        }
    }

    private void storedWithBookingsOf(Member member, Session... stored) {
        for (Session session : stored) {
            lenient().when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        }
        lenient().when(sessions.findWithLiveBookingOverlapping(eq(association.id()), eq(member.id()), any(), any()))
                .thenReturn(List.of(stored));
    }

    private DeactivateMemberCommand deactivate(Member actor) {
        return new DeactivateMemberCommand(Data.actor(actor), target.id());
    }

    private Session futureBooking(Instant start) {
        return Data.booked(Data.session(group, start, 12), target, Data.NOW.minus(Duration.ofDays(1)));
    }

    @Test
    void deactivatesTheMemberAndCancelsTheirFutureBookingsWithARefund() {
        // Arrange
        Session first = futureBooking(Data.SESSION_START);
        Session second = futureBooking(Data.SESSION_START.plus(Duration.ofDays(3)));
        storedWithBookingsOf(target, first, second);
        Subscription pack = Data.charged(Data.charged(Data.pack(association, target, 10), first,
                Data.bookingOf(first, target)), second, Data.bookingOf(second, target));
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(pack));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.member().status()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(result.bookingsCancelled()).isEqualTo(2);
        ArgumentCaptor<Member> savedMember = ArgumentCaptor.forClass(Member.class);
        verify(members).save(savedMember.capture());
        assertThat(savedMember.getValue().id()).isEqualTo(target.id());
        ArgumentCaptor<Session> savedSessions = ArgumentCaptor.forClass(Session.class);
        verify(sessions, times(2)).save(savedSessions.capture());
        assertThat(savedSessions.getAllValues()).allSatisfy(session ->
                assertThat(Data.bookingOf(session, target).cancellationKind()).hasValue(CancellationKind.BY_ASSOCIATION));
        verify(subscriptions, times(2)).save(any(Subscription.class));
    }

    @Test
    void theLastActiveAdministratorCannotBeDeactivatedNotEvenByThemselves() {
        // Arrange
        Member loneAdmin = Data.admin(association);
        knows(loneAdmin);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(loneAdmin.id()));
        Executable act = () -> useCase.execute(new DeactivateMemberCommand(Data.actor(loneAdmin), loneAdmin.id()));

        // Act
        assertThrows(LastAdministratorException.class, act);

        // Assert
        verify(members, never()).save(any(Member.class));
        verifyNoInteractions(sessions);
    }

    @Test
    void deactivatingTakesTheAssociationLockBeforeLoadingAnyMember() {
        // Arrange
        // (the default target is a plain member)

        // Act
        useCase.execute(deactivate(admin));

        // Assert
        var order = org.mockito.Mockito.inOrder(associations, members);
        order.verify(associations).findByIdForUpdate(association.id());
        order.verify(members).findById(association.id(), admin.id());
    }

    @Test
    void anAdministratorCanBeDeactivatedWhileAnotherOneStays() {
        // Arrange
        Member leaving = Data.admin(association);
        knows(leaving);
        when(members.findActiveAdminIds(association.id())).thenReturn(List.of(admin.id(), leaving.id()));
        storedWithBookingsOf(leaving);

        // Act
        MemberDeactivated result = useCase.execute(new DeactivateMemberCommand(Data.actor(admin), leaving.id()));

        // Assert
        assertThat(result.member().status()).isEqualTo(MemberStatus.INACTIVE);
        verify(members).save(any(Member.class));
    }

    @Test
    void deactivatingAnAlreadyInactiveAdministratorNeedsNoGuard() {
        // Arrange
        Member gone = Data.admin(association).deactivate();
        knows(gone);
        storedWithBookingsOf(gone);

        // Act
        MemberDeactivated result = useCase.execute(new DeactivateMemberCommand(Data.actor(admin), gone.id()));

        // Assert
        assertThat(result.member().status()).isEqualTo(MemberStatus.INACTIVE);
        verify(members, never()).findActiveAdminIds(any());
    }

    @Test
    void theCancellationIsFreeEvenInsideTheLateWindow() {
        // Arrange
        Session soon = Data.booked(Data.session(group, Data.NOW.plus(Duration.ofHours(2)), 12), target,
                Data.NOW.minus(Duration.ofDays(1)));
        storedWithBookingsOf(target, soon);
        Subscription charged = Data.charged(Data.pack(association, target, 10), soon, Data.bookingOf(soon, target));
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(charged));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isEqualTo(1);
        ArgumentCaptor<Subscription> refunded = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(refunded.capture());
        assertThat(refunded.getValue().usages()).isEmpty();
    }

    @Test
    void promotesAndChargesAndNotifiesTheNextWaitingMember() {
        // Arrange
        Member waiting = Data.member(association);
        knows(waiting);
        Session full = Data.booked(Data.session(group, 1), target, Data.NOW.minusSeconds(1_000));
        Session session = Data.booked(full, waiting, Data.NOW.minusSeconds(500));
        storedWithBookingsOf(target, session);
        when(subscriptions.findByMember(association.id(), target.id()))
                .thenReturn(List.of(Data.charged(Data.pack(association, target, 10), session, Data.bookingOf(session, target))));
        when(subscriptions.findByMember(association.id(), waiting.id()))
                .thenReturn(List.of(Data.unlimited(association, waiting)));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isEqualTo(1);
        ArgumentCaptor<Session> saved = ArgumentCaptor.forClass(Session.class);
        verify(sessions).save(saved.capture());
        assertThat(Data.bookingOf(saved.getValue(), waiting).status()).isEqualTo(BookingStatus.CONFIRMED);
        verify(notifier).bookingPromoted(association.id(), session.id(), waiting.id());
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void leavesAlreadyMarkedAndAlreadyStartedBookingsAlone() {
        // Arrange
        Instant startedAt = Data.NOW.minus(Duration.ofHours(1));
        Session started = Data.booked(Data.session(group, startedAt, 12), target, startedAt.minus(Duration.ofDays(1)));
        Session marked = started.markNoShow(Data.bookingOf(started, target).id(), Data.CLOCK);
        storedWithBookingsOf(target, marked);

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isZero();
        assertThat(result.member().status()).isEqualTo(MemberStatus.INACTIVE);
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void cancelsAWaitlistedBookingToo() {
        // Arrange
        Session full = Data.booked(Data.session(group, 1), Data.member(association), Data.NOW.minusSeconds(1_000));
        Session session = Data.booked(full, target, Data.NOW.minusSeconds(500));
        storedWithBookingsOf(target, session);

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isEqualTo(1);
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void anAlreadyInactiveMemberIsNotSavedAgainButTheirRemainingBookingsAreStillCancelled() {
        // Arrange
        Member inactive = target.deactivate();
        knows(inactive);
        Session session = futureBooking(Data.SESSION_START);
        storedWithBookingsOf(inactive, session);

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isEqualTo(1);
        assertThat(result.member().status()).isEqualTo(MemberStatus.INACTIVE);
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void onlyAnAdministratorMayDeactivate() {
        // Arrange
        Executable byCoach = () -> useCase.execute(deactivate(coach));
        Executable byMember = () -> useCase.execute(deactivate(target));

        // Act
        NotAllowedException denied = assertThrows(NotAllowedException.class, byCoach);
        NotAllowedException deniedToo = assertThrows(NotAllowedException.class, byMember);

        // Assert
        assertThat(denied.getMessage()).contains("deactivate members");
        assertThat(deniedToo).isNotNull();
        verify(members, never()).save(any(Member.class));
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void anUnknownMemberIsNotFound() {
        // Arrange
        Member stranger = Data.member(Data.association());
        Executable act = () -> useCase.execute(new DeactivateMemberCommand(Data.actor(admin), stranger.id()));

        // Act
        MemberNotFoundException ex = assertThrows(MemberNotFoundException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(stranger.id());
        verify(members, never()).save(any(Member.class));
    }

    @Test
    void aLostRaceOnASessionIsRetriedWithoutRedoingTheOthers() {
        // Arrange
        Session first = futureBooking(Data.SESSION_START);
        Session second = futureBooking(Data.SESSION_START.plus(Duration.ofDays(3)));
        storedWithBookingsOf(target, first, second);
        when(sessions.save(any(Session.class)))
                .thenThrow(new SessionModifiedConcurrentlyException(first.id()))
                .thenAnswer(returnsFirstArg());
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(
                Data.charged(Data.charged(Data.pack(association, target, 10), first, Data.bookingOf(first, target)),
                        second, Data.bookingOf(second, target))));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isEqualTo(2);
        verify(members, times(1)).save(any(Member.class));
        verify(sessions, times(3)).save(any(Session.class));
        assertThat(transactions.opened()).isEqualTo(4);
    }

    @Test
    void aSessionThatKeepsConflictingIsReportedAndTheOthersAreStillDone() {
        // Arrange
        Session busy = futureBooking(Data.SESSION_START);
        Session fine = futureBooking(Data.SESSION_START.plus(Duration.ofDays(3)));
        storedWithBookingsOf(target, busy, fine);
        when(sessions.save(any(Session.class))).thenAnswer(invocation -> {
            Session toSave = invocation.getArgument(0);
            if (toSave.id().equals(busy.id())) {
                throw new SessionModifiedConcurrentlyException(busy.id());
            }
            return toSave;
        });
        when(subscriptions.findByMember(association.id(), target.id())).thenReturn(List.of(
                Data.charged(Data.pack(association, target, 10), fine, Data.bookingOf(fine, target))));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.member().status()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(result.bookingsCancelled()).isEqualTo(1);
        assertThat(result.failedSessions()).containsExactly(busy.id());
        verify(members, times(1)).save(any(Member.class));
    }

    @Test
    void anUnexpectedFailureInOneSessionIsReportedToo() {
        // Arrange
        Session broken = futureBooking(Data.SESSION_START);
        storedWithBookingsOf(target, broken);
        when(sessions.save(any(Session.class))).thenThrow(new IllegalStateException("database exploded"));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.failedSessions()).containsExactly(broken.id());
        assertThat(result.bookingsCancelled()).isZero();
    }

    @Test
    void aSessionThatVanishedInTheMeantimeIsReportedNotFatal() {
        // Arrange
        Session gone = futureBooking(Data.SESSION_START);
        when(sessions.findWithLiveBookingOverlapping(eq(association.id()), eq(target.id()), any(), any()))
                .thenReturn(List.of(gone));
        when(sessions.findById(association.id(), gone.id())).thenReturn(Optional.empty());

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.failedSessions()).containsExactly(gone.id());
    }

    @Test
    void aSessionThatNoLongerTakesCancellationsIsSkippedWithoutCountingAsCancelledOrFailed() {
        // Arrange
        Session stale = futureBooking(Data.SESSION_START);
        Session cancelledMeanwhile = stale.cancel("Venue closed");
        when(sessions.findWithLiveBookingOverlapping(eq(association.id()), eq(target.id()), any(), any()))
                .thenReturn(List.of(stale));
        when(sessions.findById(association.id(), stale.id())).thenReturn(Optional.of(cancelledMeanwhile));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isZero();
        assertThat(result.failedSessions()).isEmpty();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void aMemberWithNoLiveBookingLeftInTheSessionIsSkipped() {
        // Arrange
        Session session = futureBooking(Data.SESSION_START);
        Session alreadyCancelled = session.cancelBooking(Data.bookingOf(session, target).id(), Data.POLICY, Data.CLOCK,
                member -> false).session();
        when(sessions.findWithLiveBookingOverlapping(eq(association.id()), eq(target.id()), any(), any()))
                .thenReturn(List.of(session));
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(alreadyCancelled));

        // Act
        MemberDeactivated result = useCase.execute(deactivate(admin));

        // Assert
        assertThat(result.bookingsCancelled()).isZero();
        assertThat(result.failedSessions()).isEmpty();
    }

    @Test
    void looksForTheMembersBookingsFromNowOnInTheActorsAssociation() {
        // Arrange
        storedWithBookingsOf(target);

        // Act
        useCase.execute(deactivate(admin));

        // Assert
        verify(sessions).findWithLiveBookingOverlapping(eq(association.id()), eq(target.id()), eq(Data.NOW), any());
    }
}
