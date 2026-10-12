package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListBookableSessionsQuery;
import com.regivolley.api.application.result.BookableSession;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.repository.AssociationRepository;
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
    @Mock
    private VenueRepository venues;

    private Association association;
    private Member coach;
    private Member member;
    private ListBookableSessionsUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        member = Data.member(association);
        useCase = new ListBookableSessionsService(sessions, members, associations, groups, venues, Data.CLOCK);
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
                groups, venues, lastSunday);

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
    void carriesTheIdOfTheMembersOwnLiveBookingOnly() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Member other = Data.member(association);
        Session mine = Data.booked(Data.booked(Data.session(group, 5), other, Data.NOW.minusSeconds(30)), member, Data.NOW.minusSeconds(20));
        Session notMine = Data.booked(Data.session(group, Data.SESSION_START.plusSeconds(86_400), 5), other, Data.NOW.minusSeconds(30));
        Session untouched = Data.session(group, Data.SESSION_START.plusSeconds(2 * 86_400), 5);
        weekReturns(mine, notMine, untouched);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week.get(0).myBookingId()).hasValue(Data.bookingOf(mine, member).id());
        assertThat(week.get(1).myBookingId()).isEmpty();
        assertThat(week.get(2).myBookingId()).isEmpty();
        assertThat(week).flatExtracting(line -> line.myBookingId().stream().toList())
                .doesNotContain(Data.bookingOf(notMine, other).id(), Data.bookingOf(mine, other).id());
    }

    @Test
    void aCancelledBookingHasNoIdOnTheLine() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session session = Data.booked(Data.session(group, 5), member, Data.NOW.minusSeconds(20));
        session = session.cancelBooking(Data.bookingOf(session, member).id(), Data.POLICY, Data.CLOCK, id -> false).session();
        weekReturns(session);

        // Act
        List<BookableSession> week = useCase.execute(thisWeek());

        // Assert
        assertThat(week.get(0).myBookingId()).isEmpty();
        assertThat(week.get(0).myWaitlistPosition()).isEmpty();
    }

    @Test
    void showsTheMembersOneBasedWaitlistPositionFromTheSessionsFifoQueue() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session session = Data.session(group, 1);
        session = Data.booked(session, Data.member(association), Data.NOW.minusSeconds(40));
        session = Data.booked(session, Data.member(association), Data.NOW.minusSeconds(30));
        session = Data.booked(session, member, Data.NOW.minusSeconds(20));
        session = Data.booked(session, Data.member(association), Data.NOW.minusSeconds(10));
        weekReturns(session);

        // Act
        BookableSession line = useCase.execute(thisWeek()).get(0);

        // Assert
        assertThat(line.myBookingStatus()).hasValue(BookingStatus.WAITLISTED);
        assertThat(line.myWaitlistPosition()).hasValue(2);
    }

    @Test
    void aConfirmedMemberHasNoWaitlistPosition() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        weekReturns(Data.booked(Data.session(group, 2), member, Data.NOW.minusSeconds(20)));

        // Act
        BookableSession line = useCase.execute(thisWeek()).get(0);

        // Assert
        assertThat(line.myWaitlistPosition()).isEmpty();
    }

    @Test
    void freeCancellationEndsSixHoursBeforeTheStartByDefault() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        Session session = Data.session(group, 5);
        weekReturns(session);

        // Act
        BookableSession line = useCase.execute(thisWeek()).get(0);

        // Assert
        assertThat(line.freeCancellationUntil()).isEqualTo(Data.SESSION_START.minus(Duration.ofHours(6)));
        assertThat(line.freeCancellationUntil()).isEqualTo(session.freeCancellationDeadline(association.bookingPolicy()));
    }

    @Test
    void freeCancellationIsSixRealHoursBeforeAStartOnTheClockChangeDay() {
        // Arrange - Sunday 25 Oct 2026: the clocks go back at 01:00Z; a 05:00Z start is 05:00 Lisbon
        Clock beforeTheChange = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC);
        ListBookableSessionsUseCase useCaseThen = new ListBookableSessionsService(sessions, members, associations, groups,
                venues, beforeTheChange);
        TrainingGroup group = groupAccepting("Beginner");
        weekReturns(Data.session(group, Instant.parse("2026-10-25T05:00:00Z"), 5));

        // Act
        BookableSession line = useCaseThen.execute(new ListBookableSessionsQuery(Data.actor(member), LocalDate.parse("2026-10-25")))
                .get(0);

        // Assert
        assertThat(line.freeCancellationUntil()).isEqualTo(Instant.parse("2026-10-24T23:00:00Z"));
    }

    @Test
    void namesTheVenueOfTheGroupAndTheGroup() {
        // Arrange
        Venue hall = Data.venue(association, "Pavilhao Central");
        TrainingGroup group = Data.groupAt(association, coach, hall, "Beginner");
        lenient().when(groups.findAllByAssociation(association.id())).thenReturn(List.of(group));
        lenient().when(venues.findAllByAssociation(association.id())).thenReturn(List.of(hall, Data.venue(association, "Other")));
        weekReturns(Data.session(group, 5));

        // Act
        BookableSession line = useCase.execute(thisWeek()).get(0);

        // Assert
        assertThat(line.venueName()).hasValue("Pavilhao Central");
        assertThat(line.groupName()).isEqualTo(group.name());
    }

    @Test
    void aGroupWhoseVenueCannotBeFoundStillListsWithoutAVenueName() {
        // Arrange
        TrainingGroup group = groupAccepting("Beginner");
        lenient().when(venues.findAllByAssociation(association.id())).thenReturn(List.of());
        weekReturns(Data.session(group, 5));

        // Act
        BookableSession line = useCase.execute(thisWeek()).get(0);

        // Assert
        assertThat(line.venueName()).isEmpty();
    }

    @Test
    void readsVenuesOnlyOfTheMembersAssociation() {
        // Arrange
        weekReturns();

        // Act
        useCase.execute(thisWeek());

        // Assert
        verify(venues).findAllByAssociation(association.id());
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
