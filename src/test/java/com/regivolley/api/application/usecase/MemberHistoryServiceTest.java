package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MemberHistoryQuery;
import com.regivolley.api.application.result.HistoryEntry;
import com.regivolley.api.application.result.MemberHistory;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberHistoryServiceTest {

    @Mock
    private SessionRepository sessions;
    @Mock
    private MemberRepository members;
    @Mock
    private AssociationRepository associations;

    private Association association;
    private Member coach;
    private Member member;
    private TrainingGroup group;
    private MemberHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        association = Data.association();
        coach = Data.coach(association);
        member = Data.member(association);
        group = Data.group(association, coach, "Beginner");
        useCase = new MemberHistoryService(sessions, members, associations, Data.CLOCK);
        lenient().when(members.findById(association.id(), member.id())).thenReturn(Optional.of(member));
        lenient().when(associations.findById(association.id())).thenReturn(Optional.of(association));
    }

    /** A past session of the member, {@code daysAgo} days before "now", ending in the given state. */
    private Session past(int daysAgo, BookingStatus outcome) {
        Instant start = Data.NOW.minus(Duration.ofDays(daysAgo));
        Clock atStart = Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC);
        Session session = Data.booked(Data.session(group, start, 12), member, start.minus(Duration.ofDays(2)));
        var id = Data.bookingOf(session, member).id();
        return switch (outcome) {
            case ATTENDED -> session.markAttended(id, atStart);
            case NO_SHOW -> session.markNoShow(id, atStart);
            default -> session;
        };
    }

    private void returns(Session... found) {
        lenient().when(sessions.findWithLiveBookingOverlapping(eq(association.id()), eq(member.id()), any(), any()))
                .thenReturn(List.of(found));
    }

    @Test
    void asksForTheLastThreeMonthsUpToNow() {
        // Arrange
        returns();

        // Act
        useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert - 12 Jul 2026 10:00 Lisbon (summer time) to now
        verify(sessions).findWithLiveBookingOverlapping(association.id(), member.id(),
                Instant.parse("2026-07-12T09:00:00Z"), Data.NOW);
    }

    @Test
    void listsTheMembersBookingsNewestFirst() {
        // Arrange
        Session older = past(30, BookingStatus.ATTENDED);
        Session newer = past(3, BookingStatus.NO_SHOW);
        returns(older, newer);

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        assertThat(history.entries()).extracting(HistoryEntry::sessionId).containsExactly(newer.id(), older.id());
        assertThat(history.entries()).extracting(HistoryEntry::status)
                .containsExactly(BookingStatus.NO_SHOW, BookingStatus.ATTENDED);
        assertThat(history.entries().get(0).bookingId()).isEqualTo(Data.bookingOf(newer, member).id());
        assertThat(history.entries().get(0).startsAt()).isEqualTo(newer.startsAt());
    }

    @Test
    void ignoresOtherMembersBookingsInTheSameSession() {
        // Arrange
        Session session = Data.booked(past(2, BookingStatus.ATTENDED), Data.member(association),
                Data.NOW.minus(Duration.ofDays(3)));
        returns(session);

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        assertThat(history.entries()).hasSize(1);
        assertThat(history.entries().get(0).status()).isEqualTo(BookingStatus.ATTENDED);
    }

    @Test
    void aMemberWhoCancelledAndBookedAgainShowsOnlyTheLiveBooking() {
        // Arrange
        Instant start = Data.NOW.minus(Duration.ofDays(2));
        Session first = Data.booked(Data.session(group, start, 12), member, start.minus(Duration.ofDays(3)));
        Session cancelled = first.cancelBooking(Data.bookingOf(first, member).id(), Data.POLICY,
                Clock.fixed(start.minus(Duration.ofDays(2)), ZoneOffset.UTC), id -> false).session();
        Session rebooked = Data.booked(cancelled, member, start.minus(Duration.ofDays(1)));
        Session attended = rebooked.markAttended(rebooked.activeBookingOf(member.id()).orElseThrow().id(),
                Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC));
        returns(attended);

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        var liveBookingId = attended.bookings().stream().filter(b -> b.status() == BookingStatus.ATTENDED)
                .findFirst().orElseThrow().id();
        assertThat(history.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.status()).isEqualTo(BookingStatus.ATTENDED);
            assertThat(entry.bookingId()).isEqualTo(liveBookingId);
        });
    }

    @Test
    void skipsOtherMembersBookingsThatComeBeforeTheirs() {
        // Arrange
        Instant start = Data.NOW.minus(Duration.ofDays(2));
        Member earlier = Data.member(association);
        Session session = Data.booked(Data.session(group, start, 12), earlier, start.minus(Duration.ofDays(3)));
        Session both = Data.booked(session, member, start.minus(Duration.ofDays(2)));
        returns(both);

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        assertThat(history.entries()).singleElement().satisfies(entry ->
                assertThat(entry.bookingId()).isEqualTo(Data.bookingOf(both, member).id()));
    }

    @Test
    void aSessionWhereTheMembersOnlyBookingIsCancelledIsLeftOut() {
        // Arrange
        Instant start = Data.NOW.minus(Duration.ofDays(2));
        Session booked = Data.booked(Data.session(group, start, 12), member, start.minus(Duration.ofDays(3)));
        Session cancelled = booked.cancelBooking(Data.bookingOf(booked, member).id(), Data.POLICY,
                Clock.fixed(start.minus(Duration.ofDays(2)), ZoneOffset.UTC), id -> false).session();
        returns(cancelled);

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        assertThat(history.entries()).isEmpty();
    }

    @Test
    void countsOnlyThisMonthsNoShowsAndIsNotNearTheLimitWithOne() {
        // Arrange - "now" is 12 Oct: 3 days ago is this month, 20 days ago is September
        returns(past(3, BookingStatus.NO_SHOW), past(20, BookingStatus.NO_SHOW), past(25, BookingStatus.NO_SHOW));

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        assertThat(history.noShowsThisMonth()).isEqualTo(1);
        assertThat(history.noShowLimit()).isEqualTo(3);
        assertThat(history.nearLimit()).isFalse();
    }

    @Test
    void isNearTheLimitOneNoShowBeforeIt() {
        // Arrange
        returns(past(1, BookingStatus.NO_SHOW), past(2, BookingStatus.NO_SHOW));

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        assertThat(history.noShowsThisMonth()).isEqualTo(2);
        assertThat(history.nearLimit()).isTrue();
    }

    @Test
    void usesTheAssociationsConfiguredLimit() {
        // Arrange
        association = association.changeNoShowPolicy(new NoShowPolicy(5));
        when(associations.findById(association.id())).thenReturn(Optional.of(association));
        returns(past(1, BookingStatus.NO_SHOW), past(2, BookingStatus.NO_SHOW), past(3, BookingStatus.NO_SHOW));

        // Act
        MemberHistory history = useCase.execute(new MemberHistoryQuery(Data.actor(member)));

        // Assert
        assertThat(history.noShowLimit()).isEqualTo(5);
        assertThat(history.nearLimit()).isFalse();
    }

    @Test
    void anUnknownMemberIsNotFound() {
        // Arrange
        Member stranger = Data.member(association);
        Executable act = () -> useCase.execute(new MemberHistoryQuery(Data.actor(stranger)));

        // Act
        MemberNotFoundException ex = assertThrows(MemberNotFoundException.class, act);

        // Assert
        assertThat(ex.memberId()).isEqualTo(stranger.id());
    }
}
