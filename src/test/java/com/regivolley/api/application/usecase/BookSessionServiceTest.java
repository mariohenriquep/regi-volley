package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.BookSessionCommand;
import com.regivolley.api.application.result.PlacedBooking;
import com.regivolley.api.domain.exception.AssociationNotFoundException;
import com.regivolley.api.domain.exception.BookingNotAllowedException;
import com.regivolley.api.domain.exception.BookingOverlapException;
import com.regivolley.api.domain.exception.BookingWindowClosedException;
import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.TrainingGroupNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookSessionServiceTest {

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

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member coach;
    private Member member;
    private TrainingGroup group;
    private Session session;
    private BookSessionUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        member = Data.member(association);
        group = Data.group(association, coach, "Beginner", "Intermediate");
        session = Data.session(group, 2);
        useCase = new BookSessionService(sessions, members, associations, groups, subscriptions, transactions, Data.CLOCK);
        lenient().when(members.findByIdForUpdate(association.id(), member.id())).thenReturn(Optional.of(member));
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group));
        lenient().when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        lenient().when(sessions.save(any(Session.class))).thenAnswer(returnsFirstArg());
        lenient().when(subscriptions.save(any(Subscription.class))).thenAnswer(returnsFirstArg());
    }

    private void memberHolds(Subscription... held) {
        lenient().when(subscriptions.findByMember(association.id(), member.id())).thenReturn(List.of(held));
    }

    private BookSessionCommand command() {
        return new BookSessionCommand(Data.actor(member), session.id());
    }

    private void assertNothingSaved() {
        verify(sessions, never()).save(any(Session.class));
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void confirmsTheMemberWhenASeatIsFreeAndChargesTheChosenSubscription() {
        // Arrange
        Subscription pack = Data.pack(association, member, 10);
        memberHolds(pack);

        // Act
        PlacedBooking placed = useCase.execute(command());

        // Assert
        assertThat(placed.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(placed.booking().memberId()).isEqualTo(member.id());
        assertThat(placed.waitlistPosition()).isEmpty();
        assertThat(placed.session().confirmedCount()).isEqualTo(1);
        ArgumentCaptor<Subscription> charged = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(charged.capture());
        assertThat(charged.getValue().id()).isEqualTo(pack.id());
        assertThat(charged.getValue().holdsPlaceFor(placed.booking().id())).isTrue();
        assertThat(charged.getValue().creditsUsed()).isEqualTo(1);
    }

    @Test
    void waitlistsTheMemberWhenTheSessionIsFullAndChargesNothing() {
        // Arrange
        Member first = Data.member(association);
        Member second = Data.member(association);
        session = Data.booked(Data.booked(session, first), second);
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        memberHolds(Data.pack(association, member, 10));

        // Act
        PlacedBooking placed = useCase.execute(command());

        // Assert
        assertThat(placed.booking().status()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(placed.waitlistPosition()).hasValue(1);
        verify(sessions).save(any(Session.class));
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void returnsTheStoredSessionNotTheOneItSent() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        Session stored = Data.session(group, 2);
        when(sessions.save(any(Session.class))).thenReturn(stored);

        // Act
        PlacedBooking placed = useCase.execute(command());

        // Assert
        assertThat(placed.session()).isSameAs(stored);
    }

    @Test
    void rejectsAnInactiveMember() {
        // Arrange
        member = member.deactivate();
        when(members.findByIdForUpdate(association.id(), member.id())).thenReturn(Optional.of(member));
        memberHolds(Data.unlimited(association, member));
        Executable act = () -> useCase.execute(command());

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.MEMBER_INACTIVE);
        assertNothingSaved();
    }

    @Test
    void rejectsAMemberWhoseLevelTheGroupDoesNotAccept() {
        // Arrange
        Member beginner = member;
        group = Data.group(association, coach, "Advanced");
        session = Data.session(group, 2);
        when(groups.findById(association.id(), group.id())).thenReturn(Optional.of(group));
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        memberHolds(Data.unlimited(association, beginner));
        Executable act = () -> useCase.execute(command());

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.LEVEL_NOT_ALLOWED);
        assertNothingSaved();
    }

    @Test
    void rejectsAMemberWithoutAnySubscription() {
        // Arrange
        memberHolds();
        Executable act = () -> useCase.execute(command());

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.NO_VALID_SUBSCRIPTION);
        assertNothingSaved();
    }

    @Test
    void rejectsAPlanThatDoesNotCoverTheGroupsLevels() {
        // Arrange
        memberHolds(Data.packFor(association, member, 10, Data.level(association, "Advanced")));
        Executable act = () -> useCase.execute(command());

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.PLAN_LEVEL_NOT_ALLOWED);
        assertNothingSaved();
    }

    @Test
    void rejectsAMemberWhoseBalanceIsSpent() {
        // Arrange
        Session earlier = Data.booked(Data.session(group, Data.SESSION_START.minusSeconds(86_400), 2), member);
        Subscription spent = Data.charged(Data.pack(association, member, 1), earlier, Data.bookingOf(earlier, member));
        memberHolds(spent);
        Executable act = () -> useCase.execute(command());

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.NO_BALANCE);
        assertNothingSaved();
    }

    @Test
    void rejectsAMemberWhoseSubscriptionIsOverdue() {
        // Arrange
        Subscription overdue = Data.unlimited(association, member);
        overdue = Subscription.reconstruct(overdue.id(), overdue.associationId(), overdue.memberId(), overdue.planId(),
                overdue.terms(), overdue.price(), overdue.startDate(), overdue.endDate(), PaymentStatus.OVERDUE, overdue.usages(), 0L);
        memberHolds(overdue);
        Executable act = () -> useCase.execute(command());

        // Act
        BookingNotAllowedException ex = assertThrows(BookingNotAllowedException.class, act);

        // Assert
        assertThat(ex.reason()).isEqualTo(BookingRejectionReason.PAYMENT_OVERDUE);
        assertNothingSaved();
    }

    @Test
    void rejectsASessionOverlappingAnotherBookingOfTheMember() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        Session other = Data.booked(Data.session(Data.group(association, coach, "Beginner"),
                session.startsAt().plusSeconds(1_800), 12), member);
        when(sessions.findWithLiveBookingOverlapping(association.id(), member.id(), session.startsAt(), session.endsAt()))
                .thenReturn(List.of(other));
        Executable act = () -> useCase.execute(command());

        // Act
        BookingOverlapException ex = assertThrows(BookingOverlapException.class, act);

        // Assert
        assertThat(ex.overlappingSessionId()).isEqualTo(other.id());
        assertNothingSaved();
    }

    @Test
    void theRepositoryOnlyNarrowsTheWindowTheSessionDecidesWhatOverlaps() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        Session backToBack = Data.booked(Data.session(Data.group(association, coach, "Beginner"), session.endsAt(), 12), member);
        when(sessions.findWithLiveBookingOverlapping(association.id(), member.id(), session.startsAt(), session.endsAt()))
                .thenReturn(List.of(backToBack));

        // Act
        PlacedBooking placed = useCase.execute(command());

        // Assert
        assertThat(placed.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void takesTheMembersRowLockBeforeReadingAnythingElse() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        var order = org.mockito.Mockito.inOrder(members, sessions, subscriptions);

        // Act
        useCase.execute(command());

        // Assert
        order.verify(members).findByIdForUpdate(association.id(), member.id());
        order.verify(sessions).findById(association.id(), session.id());
        order.verify(subscriptions).findByMember(association.id(), member.id());
    }

    @Test
    void doesNotCountTheSessionItselfAsAnOverlap() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        when(sessions.findWithLiveBookingOverlapping(association.id(), member.id(), session.startsAt(), session.endsAt()))
                .thenReturn(List.of(session));

        // Act
        PlacedBooking placed = useCase.execute(command());

        // Assert
        assertThat(placed.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void rejectsADuplicateBookingWithoutSavingAnything() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        session = Data.booked(session, member);
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        Executable act = () -> useCase.execute(command());

        // Act
        DuplicateBookingException ex = assertThrows(DuplicateBookingException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertNothingSaved();
    }

    @Test
    void rejectsABookingOutsideTheBookingWindow() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        session = Data.session(group, Data.NOW.plusSeconds(10 * 86_400L), 2);
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        Executable act = () -> useCase.execute(command());

        // Act
        BookingWindowClosedException ex = assertThrows(BookingWindowClosedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertNothingSaved();
    }

    @Test
    void rejectsTheSessionsOwnCoach() {
        // Arrange
        Member playingCoach = coach;
        when(members.findByIdForUpdate(association.id(), playingCoach.id())).thenReturn(Optional.of(playingCoach));
        when(subscriptions.findByMember(association.id(), playingCoach.id()))
                .thenReturn(List.of(Data.unlimited(association, playingCoach)));
        Executable act = () -> useCase.execute(new BookSessionCommand(Data.actor(playingCoach), session.id()));

        // Act
        CoachCannotBookOwnSessionException ex = assertThrows(CoachCannotBookOwnSessionException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertNothingSaved();
    }

    @Test
    void failsForAnUnknownSessionMemberAssociationOrGroup() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.empty());
        Executable noSession = () -> useCase.execute(command());

        // Act
        SessionNotFoundException missingSession = assertThrows(SessionNotFoundException.class, noSession);
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        when(members.findByIdForUpdate(association.id(), member.id())).thenReturn(Optional.empty());
        MemberNotFoundException missingMember = assertThrows(MemberNotFoundException.class, noSession);
        when(members.findByIdForUpdate(association.id(), member.id())).thenReturn(Optional.of(member));
        when(associations.findById(association.id())).thenReturn(Optional.empty());
        AssociationNotFoundException missingAssociation = assertThrows(AssociationNotFoundException.class, noSession);
        when(associations.findById(association.id())).thenReturn(Optional.of(association));
        when(groups.findById(association.id(), group.id())).thenReturn(Optional.empty());
        TrainingGroupNotFoundException missingGroup = assertThrows(TrainingGroupNotFoundException.class, noSession);

        // Assert
        assertThat(missingSession.sessionId()).isEqualTo(session.id());
        assertThat(missingMember.memberId()).isEqualTo(member.id());
        assertThat(missingAssociation.associationId()).isEqualTo(association.id());
        assertThat(missingGroup.trainingGroupId()).isEqualTo(group.id());
        assertNothingSaved();
    }

    @Test
    void anotherAssociationsSessionIsNotFound() {
        // Arrange
        memberHolds(Data.unlimited(association, member));
        Association other = Data.association();
        Member outsider = Data.member(other);
        when(members.findByIdForUpdate(other.id(), outsider.id())).thenReturn(Optional.of(outsider));
        Executable act = () -> useCase.execute(new BookSessionCommand(Data.actor(outsider), session.id()));

        // Act
        SessionNotFoundException ex = assertThrows(SessionNotFoundException.class, act);

        // Assert
        assertThat(ex.sessionId()).isEqualTo(session.id());
        verify(sessions).findById(other.id(), session.id());
        assertNothingSaved();
    }

    @Test
    void afterLosingTheLastSeatRaceItReadsTheSessionAgainAndWaitlistsTheMember() {
        // Arrange
        Session lastSeat = Data.session(group, 1);
        Session fullNow = Data.booked(lastSeat, Data.member(association));
        when(sessions.findById(association.id(), lastSeat.id())).thenReturn(Optional.of(lastSeat), Optional.of(fullNow));
        when(sessions.save(any(Session.class)))
                .thenThrow(new SessionModifiedConcurrentlyException(lastSeat.id()))
                .thenAnswer(returnsFirstArg());
        memberHolds(Data.pack(association, member, 10));

        // Act
        PlacedBooking placed = useCase.execute(new BookSessionCommand(Data.actor(member), lastSeat.id()));

        // Assert
        assertThat(placed.booking().status()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(placed.waitlistPosition()).hasValue(1);
        assertThat(transactions.opened()).isEqualTo(2);
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void afterLosingARaceItRejectsTheMemberWhoseSpotWasTakenBySomeoneElsesSave() {
        // Arrange
        Session lastSeat = Data.session(group, 1);
        Session alreadyBookedByMember = Data.booked(lastSeat, member);
        when(sessions.findById(association.id(), lastSeat.id())).thenReturn(Optional.of(lastSeat), Optional.of(alreadyBookedByMember));
        when(sessions.save(any(Session.class))).thenThrow(new SessionModifiedConcurrentlyException(lastSeat.id()));
        memberHolds(Data.pack(association, member, 10));
        Executable act = () -> useCase.execute(new BookSessionCommand(Data.actor(member), lastSeat.id()));

        // Act
        DuplicateBookingException ex = assertThrows(DuplicateBookingException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertThat(transactions.opened()).isEqualTo(2);
        verify(subscriptions, never()).save(any(Subscription.class));
    }

    @Test
    void retriesWhenTheSubscriptionWasChangedMeanwhile() {
        // Arrange
        Subscription pack = Data.pack(association, member, 10);
        memberHolds(pack);
        when(subscriptions.save(any(Subscription.class)))
                .thenThrow(new SubscriptionModifiedConcurrentlyException(pack.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        PlacedBooking placed = useCase.execute(command());

        // Assert
        assertThat(placed.booking().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(transactions.opened()).isEqualTo(2);
    }

    @Test
    void givesUpWithTheConflictWhenEveryAttemptLosesTheRace() {
        // Arrange
        memberHolds(Data.pack(association, member, 10));
        when(sessions.save(any(Session.class))).thenThrow(new SessionModifiedConcurrentlyException(session.id()));
        Executable act = () -> useCase.execute(command());

        // Act
        SessionModifiedConcurrentlyException ex = assertThrows(SessionModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.sessionId()).isEqualTo(session.id());
        assertThat(transactions.opened()).isEqualTo(UnitOfWork.MAX_ATTEMPTS);
        verify(subscriptions, never()).save(any(Subscription.class));
    }
}
