package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListBookableSessionsQuery;
import com.regivolley.api.application.result.BookableSession;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.LevelRank;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-13, RN-21. One query for the sessions of the member's association in a Lisbon week (Monday 00:00
 * to the next Monday 00:00 - a week with a clock change is an hour longer or shorter) and one for the
 * association's groups: the scheduled sessions that have not started yet, of the groups whose levels the
 * member may book, in the order the repository gives them. A group that accepts a level the association
 * does not have is skipped, not a failure (as in the generation). Sessions whose booking window is not open
 * yet are listed, with their {@code bookingOpensAt}. Read only, so no transaction of its own.
 */
@Service
public class ListBookableSessionsService implements ListBookableSessionsUseCase {

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final TrainingGroupRepository groups;
    private final Clock clock;

    public ListBookableSessionsService(SessionRepository sessions, MemberRepository members,
                                       AssociationRepository associations, TrainingGroupRepository groups, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.associations = associations;
        this.groups = groups;
        this.clock = clock;
    }

    @Override
    public List<BookableSession> execute(ListBookableSessionsQuery query) {
        var associationId = query.actor().associationId();
        Member member = Lookups.member(members, associationId, query.actor().memberId());
        Association association = Lookups.association(associations, associationId);
        LevelRank level = association.rankOf(member.levelId());

        LocalDate monday = Optional.ofNullable(query.dayInWeek())
                .orElseGet(() -> clock.instant().atZone(ScheduleZone.LISBON.zoneId()).toLocalDate())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = monday.atStartOfDay(ScheduleZone.LISBON.zoneId()).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(ScheduleZone.LISBON.zoneId()).toInstant();

        Instant now = clock.instant();
        Map<TrainingGroupId, TrainingGroup> groupsById = groups.findAllByAssociation(associationId).stream()
                .collect(Collectors.toMap(TrainingGroup::id, Function.identity()));
        return sessions.findStartingBetween(associationId, from, to).stream()
                .filter(session -> session.status() == SessionStatus.SCHEDULED && session.startsAt().isAfter(now))
                .filter(session -> canBook(level, association, groupsById.get(session.trainingGroupId())))
                .map(session -> line(session, groupsById.get(session.trainingGroupId()), association, member))
                .toList();
    }

    private static boolean canBook(LevelRank level, Association association, TrainingGroup group) {
        return group != null && association.hasAllLevels(group.acceptedLevels())
                && level.canBookGroupAccepting(association.ranksOf(group.acceptedLevels()));
    }

    private static BookableSession line(Session session, TrainingGroup group, Association association, Member member) {
        Optional<BookingStatus> mine = session.bookings().stream()
                .filter(booking -> booking.memberId().equals(member.id()) && booking.status() != BookingStatus.CANCELLED)
                .map(Booking::status)
                .findFirst();
        return new BookableSession(session.id(), session.trainingGroupId(), group.name(), session.startsAt(),
                session.endsAt(), session.bookingOpensAt(association.bookingPolicy()), session.capacity(),
                session.freeSeats(), session.waitlist().size(), mine);
    }
}
