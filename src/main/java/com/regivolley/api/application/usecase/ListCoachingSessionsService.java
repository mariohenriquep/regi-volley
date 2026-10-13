package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListCoachingSessionsQuery;
import com.regivolley.api.application.result.CoachingSession;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-11, US-12, US-17. The staff's week: an active coach sees the sessions they coach, an active administrator every session of the
 * association; anyone else is not allowed (the same rule as the single-session staff actions, {@link Permissions#isStaffOf}). All
 * statuses are listed - a cancelled session is something staff need to see - in the order the repository gives them (by start). Each
 * line takes its counts and its three action flags from the {@link Session} aggregate, so no rule is repeated here; the group and
 * the venue are named from one query each. The tenant is the actor's. Read only, so no transaction of its own.
 */
@Service
public class ListCoachingSessionsService implements ListCoachingSessionsUseCase {

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final TrainingGroupRepository groups;
    private final VenueRepository venues;
    private final Clock clock;

    public ListCoachingSessionsService(SessionRepository sessions, MemberRepository members, TrainingGroupRepository groups,
                                       VenueRepository venues, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.groups = groups;
        this.venues = venues;
        this.clock = clock;
    }

    @Override
    public List<CoachingSession> execute(ListCoachingSessionsQuery query) {
        AssociationId associationId = query.actor().associationId();
        Member actor = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdminOrCoach(actor, "see the coaching sessions");

        LisbonWeek week = LisbonWeek.containing(query.dayInWeek(), clock);
        Map<TrainingGroupId, TrainingGroup> groupsById = groups.findAllByAssociation(associationId).stream()
                .collect(Collectors.toMap(TrainingGroup::id, Function.identity()));
        Map<VenueId, String> venueNames = Names.ofVenues(venues, associationId);
        return sessions.findStartingBetween(associationId, week.from(), week.toExclusive()).stream()
                .filter(session -> Permissions.isStaffOf(actor, session))
                .map(session -> line(session, Optional.ofNullable(groupsById.get(session.trainingGroupId())), venueNames))
                .toList();
    }

    private CoachingSession line(Session session, Optional<TrainingGroup> group, Map<VenueId, String> venueNames) {
        return new CoachingSession(session.id(), session.trainingGroupId(), group.map(TrainingGroup::name),
                group.map(TrainingGroup::venueId).map(venueNames::get), session.coachId(), session.startsAt(), session.endsAt(),
                session.status(), session.capacity(), session.seatHolders().size(), session.waitlist().size(),
                session.attendedCount(), session.noShowCount(), session.unmarkedCount(),
                session.acceptsAttendance(clock), session.canBeCancelled(), session.acceptsCapacityChange(clock));
    }
}
