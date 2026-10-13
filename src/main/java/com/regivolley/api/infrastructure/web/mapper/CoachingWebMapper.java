package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ListCoachingSessionsQuery;
import com.regivolley.api.application.result.CoachingSession;
import com.regivolley.api.infrastructure.web.dto.CoachingSessionResponse;

import java.time.LocalDate;
import java.util.List;

/** The staff's week (US-11, US-12, US-17): the query from the caller and the date asked for, and the lines back. */
public final class CoachingWebMapper {

    private CoachingWebMapper() {
    }

    /** The week that contains {@code weekOf}; without it, the current one. */
    public static ListCoachingSessionsQuery weekQuery(Actor actor, LocalDate weekOf) {
        return new ListCoachingSessionsQuery(actor, weekOf);
    }

    public static List<CoachingSessionResponse> toResponses(List<CoachingSession> sessions) {
        return sessions.stream().map(CoachingWebMapper::toResponse).toList();
    }

    private static CoachingSessionResponse toResponse(CoachingSession session) {
        return new CoachingSessionResponse(session.sessionId().value(), session.trainingGroupId().value(),
                session.groupName().orElse(null), session.venueName().orElse(null), session.coachId().value(), session.startsAt(),
                session.endsAt(), session.status().name(), session.capacity(), session.confirmedCount(), session.waitlistCount(),
                session.attendedCount(), session.noShowCount(), session.unmarkedCount(), session.canMarkAttendance(),
                session.canCancel(), session.canChangeCapacity());
    }
}
