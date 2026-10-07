package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListBookableSessionsQuery;
import com.regivolley.api.application.result.BookableSession;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
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

@ExtendWith(MockitoExtension.class)
class ListBookableSessionsServiceTest {

    @Mock
    private SessionRepository sessions;
    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;
    @Mock
    private TrainingGroupRepository groups;

    private Association association;
    private Member coach;
    private Member member;
    private ListBookableSessionsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        member = Data.member(association);
        useCase = new ListBookableSessionsService(sessions, members, associations, groups, Data.CLOCK);
        lenient().when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
    }

    private TrainingGroup groupAccepting(String... levels) {
        TrainingGroup group = Data.group(association, coach, levels);
        lenient().when(groups.findAllByAssociation(association.id())).thenReturn(allGroups(group));
        return group;
    }

    private final List<TrainingGroup> known = new ArrayList<>();

    private List<TrainingGroup> allGroups(TrainingGroup added) {
        known.add(added);
        return List.copyOf(known);
    }

    private void weekReturns(Session... found) {
        lenient().when(sessions.findStartingBetween(eq(association.id()), any(), any())).thenReturn(List.of(found));
    }

    private ListBookableSessionsQuery thisWeek() {
        return new ListBookableSessionsQuery(Data.actor(member), null);
    }

    @Test
    void asksForTheLisbonWeekFromMondayMidnightToTheNextMondayMidnight() {
        // Arrange
        weekReturns();
        LocalDate wednesday = LocalDate.parse("2026-10-14");

        // Act
        useCase.execute(new ListBookableSessionsQuery(Data.actor(member), wednesday));

        // Assert - Monday 12 Oct 00:00 Lisbon is 23:00Z the day before (summer time)
        verify(sessions).findStartingBetween(association.id(), Instant.parse("2026-10-11T23:00:00Z"),
                Instant.parse("2026-10-18T23:00:00Z"));
    }

    @Test
    void defaultsToTheWeekOfNow() {
        // Arrange
        weekReturns();

        // Act
        useCase.execute(thisWeek());

        // Assert
        verify(sessions).findStartingBetween(association.id(), Instant.parse("2026-10-11T23:00:00Z"),
                Instant.parse("2026-10-18T23:00:00Z"));
    }

    @Test
    void theWeekOfTheClockChangeIsAHourLonger() {
        // Arrange
        weekReturns();
        Clock lastSunday = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
        ListBookableSessionsUseCase onTheChange = new ListBookableSessionsService(sessions, members, associations,
                groups, lastSunday);

        // Act
        onTheChange.execute(thisWeek());

        // Assert - Mon 19 Oct 00:00 (UTC+1) to Mon 26 Oct 00:00 (UTC+0): 169 hours
        verify(sessions).findStartingBetween(association.id(), Instant.parse("2026-10-18T23:00:00Z"),
                Instant.parse("2026-10-26T00:00:00Z"));
    }

    @Test
    void showsFreeSeatsWaitlistSizeAndTheMembersOwnStatus() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session session = Data.session(group, 2);
        session = Data.booked(session, Data.member(association), Data.NOW.minusSeconds(30));
        session = Data.booked(session, member, Data.NOW.minusSeconds(20));
        session = Data.booked(session, Data.member(association), Data.NOW.minusSeconds(10));
        session = Data.booked(session, Data.member(association), Data.NOW.minusSeconds(5));
        weekReturns(session);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).hasSize(1);
        BookableSession line = week.get(0);
        assertThat(line.sessionId()).isEqualTo(session.id());
        assertThat(line.groupName()).isEqualTo(group.name());
        assertThat(line.capacity()).isEqualTo(2);
        assertThat(line.freeSeats()).isZero();
        assertThat(line.waitlistSize()).isEqualTo(2);
        assertThat(line.myBookingStatus()).hasValue(BookingStatus.CONFIRMED);
        assertThat(line.bookingOpensAt()).isEqualTo(session.bookingOpensAt(association.bookingPolicy()));
    }

    @Test
    void showsNoOwnStatusWhenTheMemberHasNoLiveBooking() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session session = Data.session(group, 5);
        session = Data.booked(session, member, Data.NOW.minusSeconds(20));
        session = session.cancelBooking(Data.bookingOf(session, member).id(), Data.POLICY, Data.CLOCK, id -> false).session();
        Session untouched = Data.session(group, Data.SESSION_START.plusSeconds(86_400), 5);
        weekReturns(session, untouched);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).extracting(BookableSession::myBookingStatus).containsExactly(Optional.empty(), Optional.empty());
        assertThat(week.get(0).freeSeats()).isEqualTo(5);
    }

    @Test
    void onlyListsGroupsWhoseLevelsTheMemberCanBook() {
        // Arrange
        TrainingGroup open = groupAccepting("Beginner", "Intermediate");
        TrainingGroup advancedOnly = groupAccepting("Advanced");
        Session bookable = Data.session(open, 5);
        Session tooHigh = Data.session(advancedOnly, 5);
        weekReturns(bookable, tooHigh);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).extracting(BookableSession::sessionId).containsExactly(bookable.id());
    }

    @Test
    void anAdvancedMemberAlsoSeesLowerLevelGroups() {
        // Arrange
        Member advanced = Data.atLevel(Data.member(association), association, "Advanced");
        lenient().when(members.findById(association.id(), advanced.id())).thenReturn(Optional.of(advanced));
        TrainingGroup beginners = groupAccepting("Beginner");
        Session session = Data.session(beginners, 5);
        weekReturns(session);

        // Act
        List<BookableSession> week = useCase.execute(new ListBookableSessionsQuery(Data.actor(advanced), null));

        // Assert
        assertThat(week).hasSize(1);
    }

    @Test
    void skipsCancelledSessions() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session cancelled = Data.session(group, 5).cancel("Venue closed");
        weekReturns(cancelled);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).isEmpty();
    }

    @Test
    void skipsSessionsThatHaveAlreadyStarted() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session started = Data.session(group, Data.NOW.minusSeconds(60), 5);
        Session startsNow = Data.session(group, Data.NOW, 5);
        Session upcoming = Data.session(group, Data.NOW.plusSeconds(1), 5);
        weekReturns(started, startsNow, upcoming);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).extracting(BookableSession::sessionId).containsExactly(upcoming.id());
    }

    @Test
    void listsSessionsWhoseBookingWindowIsNotOpenYetWithTheirOpeningTime() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session later = Data.session(group, Data.NOW.plus(Duration.ofDays(10)), 5);
        weekReturns(later);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).hasSize(1);
        assertThat(week.get(0).bookingOpensAt()).isAfter(Data.NOW);
    }

    @Test
    void aGroupAcceptingALevelOfAnotherAssociationIsSkippedNotAFailure() {
        // Arrange
        TrainingGroup sound = groupAccepting("Beginner");
        TrainingGroup broken = Data.group(association, coach, "Beginner")
                .changeAcceptedLevels(java.util.Set.of(com.regivolley.api.domain.model.valueobject.LevelId.generate()));
        lenient().when(groups.findAllByAssociation(association.id())).thenReturn(List.of(sound, broken));
        Session fine = Data.session(sound, 5);
        Session unusable = Data.session(broken, 5);
        weekReturns(unusable, fine);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).extracting(BookableSession::sessionId).containsExactly(fine.id());
    }

    @Test
    void skipsSessionsOfAGroupItCannotFind() {
        // Arrange
        groupAccepting("Beginner");
        Session orphan = Data.session(Data.group(association, coach, "Beginner"), 5);
        weekReturns(orphan);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).isEmpty();
    }

    @Test
    void keepsTheOrderTheRepositoryGives() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session first = Data.session(group, Data.SESSION_START, 5);
        Session second = Data.session(group, Data.SESSION_START.plus(Duration.ofDays(1)), 5);
        weekReturns(first, second);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week).extracting(BookableSession::sessionId).containsExactly(first.id(), second.id());
    }

    @Test
    void anUnknownMemberIsNotFound() {
        // Arrange
        Member stranger = Data.member(association);
        Executable act = () -> useCase.execute(new ListBookableSessionsQuery(Data.actor(stranger), null));

        // Act
        MemberNotFoundException ex = assertThrows(MemberNotFoundException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(stranger.id());
    }
}
