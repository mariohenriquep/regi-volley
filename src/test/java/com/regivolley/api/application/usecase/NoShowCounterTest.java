package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoShowCounterTest {

    @Mock
    private SessionRepository sessions;

    private Association association;
    private Member member;
    private TrainingGroup group;
    private NoShowCounter counter;

    @BeforeEach
    void setUp() {
        association = Data.association();
        member = Data.member(association);
        group = Data.group(association, Data.coach(association), "Beginner");
        counter = new NoShowCounter(sessions);
    }

    private Session noShowAt(Instant start) {
        Session booked = Data.booked(Data.session(group, start, 12), member, start.minus(Duration.ofDays(1)));
        return booked.markNoShow(Data.bookingOf(booked, member).id(), Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC));
    }

    @Test
    void aSummerSessionJustBeforeLisbonMidnightCountsInTheNextMonth() {
        // Arrange - 31 Aug 23:30Z is 1 Sep 00:30 in Lisbon
        Session lateAugustUtc = noShowAt(Instant.parse("2026-08-31T23:30:00Z"));
        Instant monthStart = Instant.parse("2026-08-31T23:00:00Z");
        Instant monthEnd = Instant.parse("2026-09-30T23:00:00Z");
        when(sessions.findWithLiveBookingOverlapping(association.id(), member.id(), monthStart, monthEnd))
                .thenReturn(List.of(lateAugustUtc));

        // Act
        int inSeptember = counter.inMonthOf(association.id(), member.id(), Instant.parse("2026-09-15T10:00:00Z"));

        // Assert
        assertThat(inSeptember).isEqualTo(1);
        verify(sessions).findWithLiveBookingOverlapping(association.id(), member.id(), monthStart, monthEnd);
    }

    @Test
    void thatSameSessionDoesNotCountInAugust() {
        // Arrange - the repository returns it for any overlapping range; the counter must still filter by start
        Session lateAugustUtc = noShowAt(Instant.parse("2026-08-31T23:30:00Z"));
        when(sessions.findWithLiveBookingOverlapping(association.id(), member.id(),
                Instant.parse("2026-07-31T23:00:00Z"), Instant.parse("2026-08-31T23:00:00Z")))
                .thenReturn(List.of(lateAugustUtc));

        // Act
        int inAugust = counter.inMonthOf(association.id(), member.id(), Instant.parse("2026-08-15T10:00:00Z"));

        // Assert
        assertThat(inAugust).isZero();
    }

    @Test
    void onlyTheMembersOwnNoShowsCount() {
        // Arrange
        Instant start = Instant.parse("2026-10-05T19:00:00Z");
        Member other = Data.member(association);
        Session session = Data.booked(Data.booked(Data.session(group, start, 12), member, start.minus(Duration.ofDays(1))),
                other, start.minus(Duration.ofDays(1)).plusSeconds(1));
        Clock afterStart = Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC);
        Session marked = session.markNoShow(Data.bookingOf(session, other).id(), afterStart);
        marked = marked.markAttended(Data.bookingOf(marked, member).id(), afterStart);
        when(sessions.findWithLiveBookingOverlapping(association.id(), member.id(),
                Instant.parse("2026-09-30T23:00:00Z"), Instant.parse("2026-11-01T00:00:00Z")))
                .thenReturn(List.of(marked));

        // Act
        int count = counter.inMonthOf(association.id(), member.id(), Instant.parse("2026-10-12T09:00:00Z"));

        // Assert
        assertThat(count).isZero();
    }
}
