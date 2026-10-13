package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListCoachingSessionsQuery;
import com.regivolley.api.application.result.CoachingSession;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ListCoachingSessionsServiceTest {

    @Mock
    private SessionRepository sessions;
    @Mock
    private MemberRepository members;
    @Mock
    private TrainingGroupRepository groups;
    @Mock
    private VenueRepository venues;

    private Association association;
    private Member coach;
    private Member otherCoach;
    private Member admin;
    private TrainingGroup group;
    private TrainingGroup otherGroup;
    private ListCoachingSessionsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        otherCoach = Data.coach(association);
        admin = Data.admin(association);
        group = Data.group(association, coach, "Beginner");
        otherGroup = Data.group(association, otherCoach, "Beginner");
        useCase = new ListCoachingSessionsService(sessions, members, groups, venues, Data.CLOCK);
        for (Member known : List.of(coach, otherCoach, admin)) {
            lenient().when(members.findById(association.id(), known.id())).thenReturn(Optional.of(known));
        }
        lenient().when(groups.findAllByAssociation(association.id())).thenReturn(List.of(group, otherGroup));
    }

    private void weekReturns(Session... found) {
        lenient().when(sessions.findStartingBetween(eq(association.id()), any(), any())).thenReturn(List.of(found));
    }

    private ListCoachingSessionsQuery thisWeek(Member who) {
        return new ListCoachingSessionsQuery(Data.actor(who), null);
    }

    /** A session of {@code of}'s group that started an hour ago, with {@code people} booked before it began. */
    private Session started(TrainingGroup of, int capacity, Member... people) {
        Instant start = Data.NOW.minus(Duration.ofHours(1));
        Session session = Data.session(of, start, capacity);
        for (Member person : people) {
            session = Data.booked(session, person, start.minusSeconds(3600));
        }
        return session;
    }

    @Test
    void aCoachSeesOnlyTheSessionsTheyCoach() {
        // Arrange
        Session mine = Data.session(group, 5);
        Session theirs = Data.session(otherGroup, Data.SESSION_START.plusSeconds(60), 5);
        weekReturns(mine, theirs);

        // Act
        List<CoachingSession> week = useCase.execute(thisWeek(coach));

        // Assert
        assertThat(week).extracting(CoachingSession::sessionId).containsExactly(mine.id());
        assertThat(week.get(0).coachId()).isEqualTo(coach.id());
    }

    @Test
    void anAdministratorSeesEverySessionOfTheAssociation() {
        // Arrange
        Session mine = Data.session(group, 5);
        Session theirs = Data.session(otherGroup, Data.SESSION_START.plusSeconds(60), 5);
        weekReturns(mine, theirs);

        // Act
        List<CoachingSession> week = useCase.execute(thisWeek(admin));

        // Assert
        assertThat(week).extracting(CoachingSession::sessionId).containsExactly(mine.id(), theirs.id());
    }

    @Test
    void anAdministratorWhoAlsoCoachesStillSeesEverySession() {
        // Arrange
        Member both = Data.member(association, MemberRole.ADMIN, MemberRole.COACH);
        lenient().when(members.findById(association.id(), both.id())).thenReturn(Optional.of(both));
        weekReturns(Data.session(group, 5), Data.session(otherGroup, 5));

        // Act
        List<CoachingSession> week = useCase.execute(thisWeek(both));

        // Assert
        assertThat(week).hasSize(2);
    }

    @Test
    void aCoachWithNoSessionsGetsAnEmptyListNotAnError() {
        // Arrange
        weekReturns(Data.session(otherGroup, 5));

        // Act
        List<CoachingSession> week = useCase.execute(thisWeek(coach));

        // Assert
        assertThat(week).isEmpty();
    }

    @Test
    void aPlainMemberIsNotAllowed() {
        // Arrange
        Member plain = Data.member(association);
        lenient().when(members.findById(association.id(), plain.id())).thenReturn(Optional.of(plain));
        Executable act = () -> useCase.execute(thisWeek(plain));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(sessions);
    }

    @Test
    void aDeactivatedCoachIsNotAllowed() {
        // Arrange
        Member inactive = coach.deactivate();
        lenient().when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(inactive));
        Executable act = () -> useCase.execute(thisWeek(coach));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(sessions);
    }

    @Test
    void aMemberWhoseCoachRoleWasRevokedIsNotAllowedAnymore() {
        // Arrange
        Member former = coach.grantRole(MemberRole.MEMBER).revokeRole(MemberRole.COACH);
        lenient().when(members.findById(association.id(), coach.id())).thenReturn(Optional.of(former));
        weekReturns(Data.session(group, 5));
        Executable act = () -> useCase.execute(thisWeek(coach));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(sessions);
    }

    @Test
    void aDeactivatedAdministratorIsNotAllowed() {
        // Arrange
        lenient().when(members.findById(association.id(), admin.id())).thenReturn(Optional.of(admin.deactivate()));
        Executable act = () -> useCase.execute(thisWeek(admin));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(sessions);
    }

    @Test
    void anUnknownActorIsNotFound() {
        // Arrange
        Member stranger = Data.coach(association);
        Executable act = () -> useCase.execute(thisWeek(stranger));

        // Act
        MemberNotFoundException ex = assertThrows(MemberNotFoundException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(stranger.id());
    }

    @Test
    void readsOnlyTheActorsAssociation() {
        // Arrange
        weekReturns();

        // Act
        useCase.execute(thisWeek(admin));

        // Assert
        verify(sessions).findStartingBetween(eq(association.id()), any(), any());
        verify(groups).findAllByAssociation(association.id());
        verify(venues).findAllByAssociation(association.id());
    }

    @Test
    void asksForTheLisbonWeekFromMondayMidnightToTheNextMondayMidnight() {
        // Arrange
        weekReturns();

        // Act
        useCase.execute(new ListCoachingSessionsQuery(Data.actor(admin), LocalDate.parse("2026-10-14")));

        // Assert - Monday 12 Oct 00:00 Lisbon is 23:00Z the day before (summer time)
        verify(sessions).findStartingBetween(association.id(), Instant.parse("2026-10-11T23:00:00Z"),
                Instant.parse("2026-10-18T23:00:00Z"));
    }

    @Test
    void defaultsToTheWeekOfNow() {
        // Arrange
        weekReturns();

        // Act
        useCase.execute(thisWeek(admin));

        // Assert
        verify(sessions).findStartingBetween(association.id(), Instant.parse("2026-10-11T23:00:00Z"),
                Instant.parse("2026-10-18T23:00:00Z"));
    }

    @Test
    void theWeekOfTheClockChangeIsAHourLonger() {
        // Arrange
        weekReturns();
        Clock lastSunday = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
        ListCoachingSessionsUseCase onTheChange = new ListCoachingSessionsService(sessions, members, groups, venues, lastSunday);

        // Act
        onTheChange.execute(thisWeek(admin));

        // Assert - Mon 19 Oct 00:00 (UTC+1) to Mon 26 Oct 00:00 (UTC+0): 169 hours
        verify(sessions).findStartingBetween(association.id(), Instant.parse("2026-10-18T23:00:00Z"),
                Instant.parse("2026-10-26T00:00:00Z"));
    }

    @Test
    void listsScheduledCompletedAndCancelledSessionsInTheOrderTheRepositoryGives() {
        // Arrange
        Session cancelled = Data.session(group, Data.NOW.plus(Duration.ofHours(30)), 5).cancel("Venue closed");
        Session completed = started(group, 5).complete(Data.CLOCK);
        Session scheduled = Data.session(group, Data.SESSION_START, 5);
        weekReturns(completed, scheduled, cancelled);

        // Act
        List<CoachingSession> week = useCase.execute(thisWeek(coach));

        // Assert
        assertThat(week).extracting(CoachingSession::status)
                .containsExactly(SessionStatus.COMPLETED, SessionStatus.SCHEDULED, SessionStatus.CANCELLED);
        assertThat(week).extracting(CoachingSession::sessionId).containsExactly(completed.id(), scheduled.id(), cancelled.id());
    }

    @Test
    void carriesTheTimesCapacityAndTheNamesOfTheGroupAndItsVenue() {
        // Arrange
        Venue hall = Data.venue(association, "Pavilhao Central");
        TrainingGroup atHall = Data.groupAt(association, coach, hall, "Beginner");
        lenient().when(groups.findAllByAssociation(association.id())).thenReturn(List.of(atHall));
        lenient().when(venues.findAllByAssociation(association.id())).thenReturn(List.of(hall, Data.venue(association, "Other")));
        Session session = Data.session(atHall, 7);
        weekReturns(session);

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.trainingGroupId()).isEqualTo(atHall.id());
        assertThat(line.groupName()).hasValue(atHall.name());
        assertThat(line.venueName()).hasValue("Pavilhao Central");
        assertThat(line.startsAt()).isEqualTo(session.startsAt());
        assertThat(line.endsAt()).isEqualTo(session.endsAt());
        assertThat(line.capacity()).isEqualTo(7);
    }

    @Test
    void aVenueThatCannotBeFoundIsLeftOutNotAFailure() {
        // Arrange
        lenient().when(venues.findAllByAssociation(association.id())).thenReturn(List.of());
        weekReturns(Data.session(group, 5));

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.venueName()).isEmpty();
        assertThat(line.groupName()).hasValue(group.name());
    }

    @Test
    void aGroupThatCannotBeFoundIsLeftOutButTheSessionIsStillListed() {
        // Arrange
        lenient().when(groups.findAllByAssociation(association.id())).thenReturn(List.of());
        Session session = Data.session(group, 5);
        weekReturns(session);

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.sessionId()).isEqualTo(session.id());
        assertThat(line.groupName()).isEmpty();
        assertThat(line.venueName()).isEmpty();
    }

    @Test
    void countsSeatHoldersTheWaitlistAndEachAttendanceMark() {
        // Arrange - 5 seats... capacity 4: four seat holders (one attended, one no-show, two unmarked) and one waiting
        List<Member> people = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            people.add(Data.member(association));
        }
        Session session = started(group, 4, people.toArray(Member[]::new));
        session = session.markAttended(Data.bookingOf(session, people.get(0)).id(), Data.CLOCK);
        session = session.markNoShow(Data.bookingOf(session, people.get(1)).id(), Data.CLOCK);
        weekReturns(session);

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.confirmedCount()).isEqualTo(4);
        assertThat(line.waitlistCount()).isEqualTo(1);
        assertThat(line.attendedCount()).isEqualTo(1);
        assertThat(line.noShowCount()).isEqualTo(1);
        assertThat(line.unmarkedCount()).isEqualTo(2);
    }

    @Test
    void anEmptySessionHasAllCountsAtZero() {
        // Arrange
        weekReturns(Data.session(group, 5));

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(List.of(line.confirmedCount(), line.waitlistCount(), line.attendedCount(), line.noShowCount(), line.unmarkedCount()))
                .containsOnly(0);
    }

    @Test
    void aCancelledSessionCountsNobody() {
        // Arrange
        Session cancelled = Data.booked(Data.booked(Data.session(group, 1), Data.member(association)), Data.member(association))
                .cancel("Venue closed");
        weekReturns(cancelled);

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(List.of(line.confirmedCount(), line.waitlistCount(), line.unmarkedCount())).containsOnly(0);
    }

    @Test
    void beforeTheStartASessionCanBeCancelledAndItsCapacityChangedButNotMarked() {
        // Arrange
        weekReturns(Data.booked(Data.session(group, 5), Data.member(association)));

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.canMarkAttendance()).isFalse();
        assertThat(line.canCancel()).isTrue();
        assertThat(line.canChangeCapacity()).isTrue();
    }

    @Test
    void afterTheStartAttendanceCanBeMarkedAndCapacityIsFrozen() {
        // Arrange
        weekReturns(started(group, 5, Data.member(association)));

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert - the domain still lets a started session be cancelled until a mark is made
        assertThat(line.canMarkAttendance()).isTrue();
        assertThat(line.canChangeCapacity()).isFalse();
        assertThat(line.canCancel()).isTrue();
    }

    @Test
    void onceAttendanceIsMarkedTheSessionCanNoLongerBeCancelled() {
        // Arrange
        Member person = Data.member(association);
        Session session = started(group, 5, person, Data.member(association));
        session = session.markAttended(Data.bookingOf(session, person).id(), Data.CLOCK);
        weekReturns(session);

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert - one booking is still unmarked, so marking stays open
        assertThat(line.canCancel()).isFalse();
        assertThat(line.canMarkAttendance()).isTrue();
    }

    @Test
    void whenEveryoneIsMarkedThereIsNothingLeftToMark() {
        // Arrange
        Member person = Data.member(association);
        Session session = started(group, 5, person);
        session = session.markNoShow(Data.bookingOf(session, person).id(), Data.CLOCK);
        weekReturns(session);

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.canMarkAttendance()).isFalse();
        assertThat(line.canCancel()).isFalse();
        assertThat(line.canChangeCapacity()).isFalse();
    }

    @Test
    void aCancelledSessionOffersNoAction() {
        // Arrange
        weekReturns(Data.session(group, 5).cancel("Venue closed"));

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.canMarkAttendance()).isFalse();
        assertThat(line.canCancel()).isFalse();
        assertThat(line.canChangeCapacity()).isFalse();
    }

    @Test
    void aCompletedSessionCanOnlyHaveItsRemainingAttendanceMarked() {
        // Arrange
        weekReturns(started(group, 5, Data.member(association)).complete(Data.CLOCK));

        // Act
        CoachingSession line = useCase.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.canMarkAttendance()).isTrue();
        assertThat(line.canCancel()).isFalse();
        assertThat(line.canChangeCapacity()).isFalse();
    }

    @Test
    void theFlagsFollowTheInjectedClock() {
        // Arrange - the same session, the clock moved to its first second
        Session session = Data.booked(Data.session(group, 5), Data.member(association));
        weekReturns(session);
        Clock atStart = Clock.fixed(session.startsAt(), ZoneOffset.UTC);
        ListCoachingSessionsUseCase later = new ListCoachingSessionsService(sessions, members, groups, venues, atStart);

        // Act
        CoachingSession line = later.execute(thisWeek(coach)).get(0);

        // Assert
        assertThat(line.canMarkAttendance()).isTrue();
        assertThat(line.canChangeCapacity()).isFalse();
    }
}
