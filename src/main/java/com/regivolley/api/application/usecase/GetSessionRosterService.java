package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetSessionRosterQuery;
import com.regivolley.api.application.result.CoachingSession;
import com.regivolley.api.application.result.RosterEntry;
import com.regivolley.api.application.result.SessionRoster;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * US-17. The coach of the session or an administrator reads the session with its live bookings - the seat holders the domain
 * reports ({@link Session#seatHolders()}: CONFIRMED, ATTENDED, NO_SHOW), then the waitlist in promotion order - so they can learn
 * the booking ids that {@code MarkAttendance} takes. The tenant is the actor's: a session of another association is not found. A
 * booking shows the member's display name and nothing else about them (no email, no phone). The members are read in one batch.
 * The roster also carries the session's coaching line - counts and the three action flags, the same {@link CoachingLines} the
 * week list uses, answered by the {@link Session} - so a screen needs no second request to know what it may offer. Read only,
 * so no transaction of its own.
 */
@Service
public class GetSessionRosterService implements GetSessionRosterUseCase {

    /** Shown when a booking's member cannot be loaded, so the booking can still be marked. */
    static final String UNKNOWN_MEMBER = "Unknown member";

    private static final Logger LOG = LoggerFactory.getLogger(GetSessionRosterService.class);

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final TrainingGroupRepository groups;
    private final VenueRepository venues;
    private final Clock clock;

    public GetSessionRosterService(SessionRepository sessions, MemberRepository members, TrainingGroupRepository groups,
                                   VenueRepository venues, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.groups = groups;
        this.venues = venues;
        this.clock = clock;
    }

    @Override
    public SessionRoster execute(GetSessionRosterQuery query) {
        AssociationId associationId = query.actor().associationId();
        Member actor = Lookups.member(members, associationId, query.actor().memberId());
        Session session = Lookups.session(sessions, associationId, query.sessionId());
        Permissions.requireStaffOf(actor, session, "see who is in this session");

        List<Booking> seats = session.seatHolders();
        List<Booking> waitlist = session.waitlist();
        Map<MemberId, Member> people = load(associationId, Stream.concat(seats.stream(), waitlist.stream()).toList());
        return new SessionRoster(coachingLine(associationId, session),
                seats.stream().map(booking -> entry(booking, people)).toList(),
                waitlist.stream().map(booking -> entry(booking, people)).toList());
    }

    private CoachingSession coachingLine(AssociationId associationId, Session session) {
        Optional<TrainingGroup> group = groups.findById(associationId, session.trainingGroupId());
        Optional<String> venueName = group.map(TrainingGroup::venueId)
                .flatMap(venueId -> venues.findById(associationId, venueId)).map(Venue::name);
        return CoachingLines.of(session, group, venueName, clock);
    }

    private Map<MemberId, Member> load(AssociationId associationId, List<Booking> bookings) {
        Set<MemberId> ids = bookings.stream().map(Booking::memberId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return members.findByIds(associationId, ids).stream().collect(Collectors.toMap(Member::id, Function.identity()));
    }

    private static RosterEntry entry(Booking booking, Map<MemberId, Member> people) {
        Member person = people.get(booking.memberId());
        if (person == null) {
            LOG.warn("A booking's member could not be loaded: booking={} member={}", booking.id(), booking.memberId());
        }
        return new RosterEntry(booking.id(), booking.memberId(), person == null ? UNKNOWN_MEMBER : person.name(), booking.status());
    }
}
