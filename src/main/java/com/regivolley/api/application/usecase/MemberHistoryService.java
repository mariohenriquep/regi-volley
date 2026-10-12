package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MemberHistoryQuery;
import com.regivolley.api.application.result.HistoryEntry;
import com.regivolley.api.application.result.MemberHistory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-18. The member's sessions from three Lisbon calendar months ago up to now, newest first, and this
 * month's no-show count against the association's limit (RN-11). It lists the bookings the repository
 * reports as live (waitlisted, confirmed, attended, no-show); a cancelled booking is not part of it.
 * Each entry names its group and the venue when they can be found. Only the actor's own history is ever returned.
 */
@Service
public class MemberHistoryService implements MemberHistoryUseCase {

    private static final int MONTHS_BACK = 3;

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final TrainingGroupRepository groups;
    private final VenueRepository venues;
    private final NoShowCounter noShows;
    private final Clock clock;

    public MemberHistoryService(SessionRepository sessions, MemberRepository members,
                                AssociationRepository associations, TrainingGroupRepository groups,
                                VenueRepository venues, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.associations = associations;
        this.groups = groups;
        this.venues = venues;
        this.noShows = new NoShowCounter(sessions);
        this.clock = clock;
    }

    @Override
    public MemberHistory execute(MemberHistoryQuery query) {
        var associationId = query.actor().associationId();
        Member member = Lookups.member(members, associationId, query.actor().memberId());
        Association association = Lookups.association(associations, associationId);
        Instant now = clock.instant();
        Instant from = now.atZone(ScheduleZone.LISBON.zoneId()).minusMonths(MONTHS_BACK).toInstant();

        Map<TrainingGroupId, TrainingGroup> groupsById = groups.findAllByAssociation(associationId).stream()
                .collect(Collectors.toMap(TrainingGroup::id, Function.identity(), (first, second) -> first));
        Map<VenueId, String> venueNames = Names.ofVenues(venues, associationId);
        List<HistoryEntry> entries = sessions.findWithLiveBookingOverlapping(associationId, member.id(), from, now)
                .stream()
                .sorted(Comparator.comparing(Session::startsAt).reversed())
                .map(session -> entryOf(session, member, Optional.ofNullable(groupsById.get(session.trainingGroupId())), venueNames))
                .flatMap(Optional::stream)
                .toList();

        NoShowPolicy policy = association.noShowPolicy();
        int noShowsThisMonth = noShows.inMonthOf(associationId, member.id(), now);
        return new MemberHistory(entries, noShowsThisMonth, policy.monthlyLimit(), policy.isNear(noShowsThisMonth));
    }

    private static Optional<HistoryEntry> entryOf(Session session, Member member, Optional<TrainingGroup> group,
                                                  Map<VenueId, String> venueNames) {
        return session.liveBookingOf(member.id()).map(booking -> toEntry(session, booking, group, venueNames));
    }

    private static HistoryEntry toEntry(Session session, Booking booking, Optional<TrainingGroup> group,
                                        Map<VenueId, String> venueNames) {
        return new HistoryEntry(session.id(), session.trainingGroupId(), group.map(TrainingGroup::name),
                group.map(TrainingGroup::venueId).map(venueNames::get), session.startsAt(), session.endsAt(), booking.id(),
                booking.status());
    }
}
