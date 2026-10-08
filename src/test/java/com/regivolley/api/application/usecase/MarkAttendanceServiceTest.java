package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AttendanceEntry;
import com.regivolley.api.application.command.AttendanceMark;
import com.regivolley.api.application.command.MarkAttendanceCommand;
import com.regivolley.api.application.result.AttendanceMarked;
import com.regivolley.api.domain.exception.BookingNotFoundException;
import com.regivolley.api.domain.exception.InvalidBookingStatusTransitionException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.exception.SessionNotStartedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkAttendanceServiceTest {

    /** The session started 30 minutes ago, so attendance can be marked. */
    private static final Instant STARTED = Data.NOW.minus(Duration.ofMinutes(30));

    @Mock
    private SessionRepository sessions;
    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private Notifier notifier;

    private final DirectTransactions transactions = new DirectTransactions();

    private Association association;
    private Member coach;
    private TrainingGroup group;
    private MarkAttendanceUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        group = Data.group(association, coach, "Beginner");
        useCase = new MarkAttendanceService(sessions, members, associations, transactions, notifier, Data.CLOCK);
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
        lenient().when(sessions.save(any(Session.class))).thenAnswer(returnsFirstArg());
        knows(coach);
    }

    private void knows(Member person) {
        lenient().when(members.findById(association.id(), person.id())).thenReturn(Optional.of(person));
    }

    private Session sessionWith(Instant start, Member... players) {
        Session session = Data.session(group, start, 12);
        for (Member player : players) {
            session = Data.booked(session, player, start.minus(Duration.ofDays(1)));
        }
        lenient().when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        return session;
    }

    private MarkAttendanceCommand mark(Member actor, Session session, AttendanceEntry... entries) {
        return new MarkAttendanceCommand(Data.actor(actor), session.id(), List.of(entries));
    }

    private static AttendanceEntry entry(Session session, Member member, AttendanceMark mark) {
        return new AttendanceEntry(Data.bookingOf(session, member).id(), mark);
    }

    private static Session noShow(Session session, Member member) {
        return session.markNoShow(Data.bookingOf(session, member).id(), Data.CLOCK);
    }

    /** What the repository returns for the member's month once the new no-show is stored. */
    private void monthHistory(Session... sessionsOfTheMonth) {
        lenient().when(sessions.findWithLiveBookingOverlapping(any(), any(), any(), any()))
                .thenReturn(List.of(sessionsOfTheMonth));
    }

    /** An earlier session of the same member, already marked no-show. */
    private Session earlierNoShow(Member member, int daysBefore) {
        Instant start = STARTED.minus(Duration.ofDays(daysBefore));
        Session session = Data.booked(Data.session(group, start, 12), member, start.minus(Duration.ofDays(1)));
        return session.markNoShow(Data.bookingOf(session, member).id(), Clock.fixed(start.plusSeconds(1), ZoneOffset.UTC));
    }

    @Test
    void marksEachBookingAttendedOrNoShowAndStoresTheSession() {
        // Arrange
        Member present = Data.member(association);
        Member absent = Data.member(association);
        Session session = sessionWith(STARTED, present, absent);
        monthHistory();

        // Act
        AttendanceMarked result = useCase.execute(mark(coach, session,
                entry(session, present, AttendanceMark.ATTENDED), entry(session, absent, AttendanceMark.NO_SHOW)));

        // Assert
        assertThat(Data.bookingOf(result.session(), present).status()).isEqualTo(BookingStatus.ATTENDED);
        assertThat(Data.bookingOf(result.session(), absent).status()).isEqualTo(BookingStatus.NO_SHOW);
        assertThat(result.warnedMembers()).isEmpty();
        verify(sessions).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void warnsTheMemberAndAdministratorsExactlyWhenTheThirdNoShowOfTheMonthIsMarked() {
        // Arrange
        Member absent = Data.member(association);
        Session session = sessionWith(STARTED, absent);
        monthHistory(noShow(session, absent), earlierNoShow(absent, 2), earlierNoShow(absent, 4));

        // Act
        AttendanceMarked result = useCase.execute(mark(coach, session, entry(session, absent, AttendanceMark.NO_SHOW)));

        // Assert
        assertThat(result.warnedMembers()).containsExactly(absent.id());
        verify(notifier).noShowLimitReached(association.id(), absent.id(), 3);
        verify(sessions).save(any(Session.class));
    }

    @Test
    void doesNotWarnBelowTheLimit() {
        // Arrange
        Member absent = Data.member(association);
        Session session = sessionWith(STARTED, absent);
        monthHistory(noShow(session, absent), earlierNoShow(absent, 2));

        // Act
        AttendanceMarked result = useCase.execute(mark(coach, session, entry(session, absent, AttendanceMark.NO_SHOW)));

        // Assert
        assertThat(result.warnedMembers()).isEmpty();
        verifyNoInteractions(notifier);
    }

    @Test
    void doesNotWarnAgainBeyondTheLimitAndNeverBlocks() {
        // Arrange
        Member absent = Data.member(association);
        Session session = sessionWith(STARTED, absent);
        monthHistory(noShow(session, absent), earlierNoShow(absent, 1), earlierNoShow(absent, 2), earlierNoShow(absent, 3));

        // Act
        AttendanceMarked result = useCase.execute(mark(coach, session, entry(session, absent, AttendanceMark.NO_SHOW)));

        // Assert
        assertThat(result.warnedMembers()).isEmpty();
        assertThat(Data.bookingOf(result.session(), absent).status()).isEqualTo(BookingStatus.NO_SHOW);
        verifyNoInteractions(notifier);
    }

    @Test
    void usesTheLimitConfiguredForTheAssociation() {
        // Arrange
        association = association.changeNoShowPolicy(new NoShowPolicy(1));
        when(associations.findById(association.id())).thenReturn(Optional.of(association));
        Member absent = Data.member(association);
        Session session = sessionWith(STARTED, absent);
        monthHistory(noShow(session, absent));

        // Act
        AttendanceMarked result = useCase.execute(mark(coach, session, entry(session, absent, AttendanceMark.NO_SHOW)));

        // Assert
        assertThat(result.warnedMembers()).containsExactly(absent.id());
        verify(notifier).noShowLimitReached(association.id(), absent.id(), 1);
    }

    @Test
    void noShowsOfAnotherMonthDoNotCount() {
        // Arrange
        Member absent = Data.member(association);
        Session session = sessionWith(STARTED, absent);
        monthHistory(noShow(session, absent), earlierNoShow(absent, 20), earlierNoShow(absent, 25));

        // Act
        AttendanceMarked result = useCase.execute(mark(coach, session, entry(session, absent, AttendanceMark.NO_SHOW)));

        // Assert
        assertThat(result.warnedMembers()).isEmpty();
        verifyNoInteractions(notifier);
    }

    @Test
    void anAdministratorMayMarkButAMemberOrAnotherCoachMayNot() {
        // Arrange
        Member admin = Data.admin(association);
        Member member = Data.member(association);
        Member otherCoach = Data.coach(association);
        knows(admin);
        knows(member);
        knows(otherCoach);
        Member player = Data.member(association);
        Session session = sessionWith(STARTED, player);
        AttendanceEntry attended = entry(session, player, AttendanceMark.ATTENDED);
        Executable byMember = () -> useCase.execute(mark(member, session, attended));
        Executable byOtherCoach = () -> useCase.execute(mark(otherCoach, session, attended));

        // Act
        AttendanceMarked byAdmin = useCase.execute(mark(admin, session, attended));
        NotAllowedException denied = assertThrows(NotAllowedException.class, byMember);
        NotAllowedException deniedToo = assertThrows(NotAllowedException.class, byOtherCoach);

        // Assert
        assertThat(byAdmin.session().bookings()).extracting(Booking::status).containsExactly(BookingStatus.ATTENDED);
        assertThat(denied.getMessage()).contains("mark attendance");
        assertThat(deniedToo).isNotNull();
        verify(sessions, times(1)).save(any(Session.class));
    }

    @Test
    void cannotMarkBeforeTheSessionStarts() {
        // Arrange
        Member player = Data.member(association);
        Session session = sessionWith(Data.NOW.plus(Duration.ofHours(1)), player);
        Executable act = () -> useCase.execute(mark(coach, session, entry(session, player, AttendanceMark.ATTENDED)));

        // Act
        SessionNotStartedException ex = assertThrows(SessionNotStartedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void aWaitlistedBookingCannotBeMarkedAndNothingIsSaved() {
        // Arrange
        Member present = Data.member(association);
        Member waiting = Data.member(association);
        Session full = Data.session(group, STARTED, 1);
        Session session = Data.booked(Data.booked(full, present, STARTED.minus(Duration.ofDays(1))), waiting,
                STARTED.minus(Duration.ofHours(20)));
        when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        Executable act = () -> useCase.execute(mark(coach, session,
                entry(session, present, AttendanceMark.ATTENDED), entry(session, waiting, AttendanceMark.ATTENDED)));

        // Act
        InvalidBookingStatusTransitionException ex = assertThrows(InvalidBookingStatusTransitionException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verify(sessions, never()).save(any(Session.class));
        verifyNoInteractions(notifier);
    }

    @Test
    void unknownSessionOrBookingFails() {
        // Arrange
        Session unknown = Data.session(group, STARTED, 2);
        Executable noSession = () -> useCase.execute(new MarkAttendanceCommand(Data.actor(coach), unknown.id(), List.of()));
        Session known = sessionWith(STARTED);
        Executable noBooking = () -> useCase.execute(new MarkAttendanceCommand(Data.actor(coach), known.id(),
                List.of(new AttendanceEntry(BookingId.generate(), AttendanceMark.NO_SHOW))));

        // Act
        SessionNotFoundException missingSession = assertThrows(SessionNotFoundException.class, noSession);
        BookingNotFoundException missingBooking = assertThrows(BookingNotFoundException.class, noBooking);

        // Assert
        assertThat(missingSession.sessionId()).isEqualTo(unknown.id());
        assertThat(missingBooking).isNotNull();
        verify(sessions, never()).save(any(Session.class));
    }

    @Test
    void retriesALostRaceAndWarnsOnlyOnce() {
        // Arrange
        Member absent = Data.member(association);
        Session session = sessionWith(STARTED, absent);
        monthHistory(noShow(session, absent), earlierNoShow(absent, 2), earlierNoShow(absent, 4));
        when(sessions.save(any(Session.class)))
                .thenThrow(new SessionModifiedConcurrentlyException(session.id()))
                .thenAnswer(returnsFirstArg());

        // Act
        AttendanceMarked result = useCase.execute(mark(coach, session, entry(session, absent, AttendanceMark.NO_SHOW)));

        // Assert
        assertThat(result.warnedMembers()).containsExactly(absent.id());
        assertThat(transactions.opened()).isEqualTo(2);
        verify(notifier, times(1)).noShowLimitReached(association.id(), absent.id(), 3);
    }
}
