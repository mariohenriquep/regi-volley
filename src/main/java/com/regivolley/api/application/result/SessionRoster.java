package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A session and who is in it (US-17). {@code seats} are the bookings that hold a seat - CONFIRMED, and ATTENDED / NO_SHOW once
 * marked - in the order they were requested; {@code waitlist} is WAITLISTED in promotion order. Cancelled bookings are not part of it.
 */
public record SessionRoster(SessionId sessionId, TrainingGroupId trainingGroupId, MemberId coachId, Instant startsAt, Instant endsAt,
                            int capacity, SessionStatus status, List<RosterEntry> seats, List<RosterEntry> waitlist) {

    public SessionRoster {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(trainingGroupId, "trainingGroupId must not be null");
        Objects.requireNonNull(coachId, "coachId must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        seats = List.copyOf(seats);
        waitlist = List.copyOf(waitlist);
    }
}
