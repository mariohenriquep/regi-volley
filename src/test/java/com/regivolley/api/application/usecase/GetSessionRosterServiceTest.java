package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetSessionRosterQuery;
import com.regivolley.api.application.result.RosterEntry;
import com.regivolley.api.application.result.SessionRoster;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.exception.SessionNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** US-17: the staff of a session read who holds a seat or a waitlist place, with the booking ids attendance needs. */
@ExtendWith(MockitoExtension.class)
class GetSessionRosterServiceTest {

    private static final Instant STARTED = Data.NOW.minus(Duration.ofMinutes(30));

    @Mock
    private SessionRepository sessions;
    @Mock
    private MemberRepository members;

    private Association association;
    private Member coach;
    private TrainingGroup group;
    private GetSessionRosterUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        group = Data.group(association, coach, "Beginner");
        useCase = new GetSessionRosterService(sessions, members);
        knows(coach);
    }

    private void knows(Member person) {
        lenient().when(members.findById(association.id(), person.id())).thenReturn(Optional.of(person));
    }

    private Session stored(Session session) {
        lenient().when(sessions.findById(association.id(), session.id())).thenReturn(Optional.of(session));
        return session;
    }

    private void loadsMembers(Member... people) {
        when(members.findByIds(any(), any())).thenReturn(List.of(people));
    }

    private GetSessionRosterQuery query(Member actor, Session session) {
        return new GetSessionRosterQuery(Data.actor(actor), session.id());
    }

    @Test
    void theCoachSeesSeatsAndTheWaitlistInOrderWithNamesAndBookingIds() {
        // Arrange
        Member ana = Data.member(association);
        Member bruno = Data.member(association);
        Member carla = Data.member(association);
        Member diogo = Data.member(association);
        Session session = Data.session(group, Data.SESSION_START, 2);
        session = Data.booked(session, ana, Data.NOW.minus(Duration.ofHours(4)));
        session = Data.booked(session, bruno, Data.NOW.minus(Duration.ofHours(3)));
        session = Data.booked(session, carla, Data.NOW.minus(Duration.ofHours(2)));
        session = Data.booked(session, diogo, Data.NOW.minus(Duration.ofHours(1)));
        stored(session);
        loadsMembers(ana, bruno, carla, diogo);

        // Act
        SessionRoster roster = useCase.execute(query(coach, session));

        // Assert
        assertThat(roster.sessionId()).isEqualTo(session.id());
        assertThat(roster.trainingGroupId()).isEqualTo(group.id());
        assertThat(roster.coachId()).isEqualTo(coach.id());
        assertThat(roster.startsAt()).isEqualTo(Data.SESSION_START);
        assertThat(roster.capacity()).isEqualTo(2);
        assertThat(roster.status()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(roster.seats()).extracting(RosterEntry::memberId).containsExactly(ana.id(), bruno.id());
        assertThat(roster.seats()).extracting(RosterEntry::memberName).containsExactly(ana.name(), bruno.name());
        assertThat(roster.seats()).extracting(RosterEntry::status).containsOnly(BookingStatus.CONFIRMED);
        assertThat(roster.seats().get(0).bookingId()).isEqualTo(Data.bookingOf(session, ana).id());
        assertThat(roster.waitlist()).extracting(RosterEntry::memberId).containsExactly(carla.id(), diogo.id());
        assertThat(roster.waitlist()).extracting(RosterEntry::status).containsOnly(BookingStatus.WAITLISTED);
        assertThat(roster.waitlist().get(1).bookingId()).isEqualTo(Data.bookingOf(session, diogo).id());
    }

    @Test
    void marksAlreadyGivenStayOnTheSeatedList() {
        // Arrange
        Member ana = Data.member(association);
        Member bruno = Data.member(association);
        Instant booked = STARTED.minus(Duration.ofDays(1));
        Session session = Data.session(group, STARTED, 12);
        session = Data.booked(session, ana, booked);
        session = Data.booked(session, bruno, booked.plusSeconds(1));
        session = session.markAttended(Data.bookingOf(session, ana).id(), Data.CLOCK);
        session = session.markNoShow(Data.bookingOf(session, bruno).id(), Data.CLOCK);
        stored(session);
        loadsMembers(ana, bruno);

        // Act
        SessionRoster roster = useCase.execute(query(coach, session));

        // Assert
        assertThat(roster.seats()).extracting(RosterEntry::status).containsExactly(BookingStatus.ATTENDED, BookingStatus.NO_SHOW);
        assertThat(roster.seats()).extracting(RosterEntry::memberId).containsExactly(ana.id(), bruno.id());
        assertThat(roster.waitlist()).isEmpty();
    }

    @Test
    void cancelledBookingsAreLeftOut() {
        // Arrange
        Member ana = Data.member(association);
        Member bruno = Data.member(association);
        Session session = Data.session(group, 12);
        session = Data.booked(session, ana, Data.NOW.minus(Duration.ofHours(2)));
        session = Data.booked(session, bruno, Data.NOW.minus(Duration.ofHours(1)));
        session = session.cancelBooking(Data.bookingOf(session, bruno).id(), Data.POLICY, Data.CLOCK, member -> true).session();
        stored(session);
        loadsMembers(ana);

        // Act
        SessionRoster roster = useCase.execute(query(coach, session));

        // Assert
        assertThat(roster.seats()).extracting(RosterEntry::memberId).containsExactly(ana.id());
        assertThat(roster.waitlist()).isEmpty();
        verify(members).findByIds(association.id(), Set.<MemberId>of(ana.id()));
    }

    @Test
    void anAdministratorMayReadTheRosterOfAnotherCoachsSession() {
        // Arrange
        Member admin = Data.admin(association);
        knows(admin);
        Member ana = Data.member(association);
        Session session = stored(Data.booked(Data.session(group, 12), ana));
        loadsMembers(ana);

        // Act
        SessionRoster roster = useCase.execute(query(admin, session));

        // Assert
        assertThat(roster.seats()).extracting(RosterEntry::memberId).containsExactly(ana.id());
    }

    @Test
    void membersAreLoadedInOneBatchInsideTheActorsAssociation() {
        // Arrange
        Member ana = Data.member(association);
        Member bruno = Data.member(association);
        Session session = stored(Data.booked(Data.booked(Data.session(group, 12), ana), bruno));
        loadsMembers(ana, bruno);

        // Act
        useCase.execute(query(coach, session));

        // Assert
        verify(members).findByIds(association.id(), Set.<MemberId>of(ana.id(), bruno.id()));
    }

    @Test
    void aBookingWhoseMemberCannotBeLoadedIsStillListedSoTheCoachCanMarkIt() {
        // Arrange
        Member ana = Data.member(association);
        Session session = stored(Data.booked(Data.session(group, 12), ana));
        loadsMembers();

        // Act
        SessionRoster roster = useCase.execute(query(coach, session));

        // Assert
        assertThat(roster.seats()).hasSize(1);
        assertThat(roster.seats().get(0).memberId()).isEqualTo(ana.id());
        assertThat(roster.seats().get(0).memberName()).isEqualTo(GetSessionRosterService.UNKNOWN_MEMBER);
    }

    @Test
    void aSessionWithNoLiveBookingsHasAnEmptyRosterAndLoadsNoMember() {
        // Arrange
        Session session = stored(Data.session(group, 12));

        // Act
        SessionRoster roster = useCase.execute(query(coach, session));

        // Assert
        assertThat(roster.seats()).isEmpty();
        assertThat(roster.waitlist()).isEmpty();
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void aPlainMemberIsRefusedAndNoOneElseIsLoaded() {
        // Arrange
        Member plain = Data.member(association);
        knows(plain);
        Session session = stored(Data.booked(Data.session(group, 12), plain));
        Executable act = () -> useCase.execute(query(plain, session));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void aCoachOfAnotherSessionIsRefused() {
        // Arrange
        Member otherCoach = Data.coach(association);
        knows(otherCoach);
        Session session = stored(Data.session(group, 12));
        Executable act = () -> useCase.execute(query(otherCoach, session));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void aDeactivatedCoachHoldsNoPowerOverTheirOwnSession() {
        // Arrange
        Member inactive = coach.deactivate();
        knows(inactive);
        Session session = stored(Data.session(group, 12));
        Executable act = () -> useCase.execute(query(inactive, session));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void aCoachWhoseCoachRoleWasRevokedHoldsNoPowerOverTheSessionTheyWereGiven() {
        // Arrange
        Member former = coach.grantRole(com.regivolley.api.domain.model.valueobject.MemberRole.MEMBER)
                .revokeRole(com.regivolley.api.domain.model.valueobject.MemberRole.COACH);
        knows(former);
        Session session = stored(Data.session(group, 12));
        Executable act = () -> useCase.execute(query(former, session));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void aCancelledSessionHasAnEmptyRosterAndKeepsItsStatus() {
        // Arrange
        Member ana = Data.member(association);
        Member bruno = Data.member(association);
        Session session = Data.session(group, 1);
        session = Data.booked(Data.booked(session, ana, Data.NOW.minus(Duration.ofHours(2))), bruno, Data.NOW.minus(Duration.ofHours(1)));
        stored(session.cancel("Venue closed"));

        // Act
        SessionRoster roster = useCase.execute(new GetSessionRosterQuery(Data.actor(coach), session.id()));

        // Assert
        assertThat(roster.status()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(roster.seats()).isEmpty();
        assertThat(roster.waitlist()).isEmpty();
        verify(members, never()).findByIds(any(), any());
    }

    @Test
    void aSessionOfAnotherAssociationIsNotFound() {
        // Arrange
        Session foreign = Data.session(group, 12);
        when(sessions.findById(association.id(), foreign.id())).thenReturn(Optional.empty());
        Executable act = () -> useCase.execute(query(coach, foreign));

        // Act
        assertThrows(SessionNotFoundException.class, act);

        // Assert
        verify(sessions).findById(association.id(), foreign.id());
        verifyNoMoreInteractions(sessions);
    }
}
