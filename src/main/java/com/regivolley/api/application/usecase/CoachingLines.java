package com.regivolley.api.application.usecase;

import com.regivolley.api.application.result.CoachingSession;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;

import java.time.Clock;
import java.util.Optional;

/**
 * The staff's line for one session - its counts and the three action flags, all answered by the {@link Session} aggregate with the
 * clock - shared by the week list and the roster, so the two never disagree and no rule is repeated.
 */
final class CoachingLines {

    private CoachingLines() {
    }

    static CoachingSession of(Session session, Optional<TrainingGroup> group, Optional<String> venueName, Clock clock) {
        return new CoachingSession(session.id(), session.trainingGroupId(), group.map(TrainingGroup::name), venueName,
                session.coachId(), session.startsAt(), session.endsAt(), session.status(), session.capacity(),
                session.seatHolders().size(), session.waitlist().size(), session.attendedCount(), session.noShowCount(),
                session.unmarkedCount(), session.acceptsAttendance(clock), session.canBeCancelled(),
                session.acceptsCapacityChange(clock));
    }
}
