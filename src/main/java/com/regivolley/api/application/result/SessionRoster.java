package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A session and who is in it (US-17). {@code coaching} is the session's own line of the staff's week - when and where, the counts and
 * the action flags the {@code Session} answers - so the roster and the week list never disagree. {@code seats} are the bookings that
 * hold a seat - CONFIRMED, and ATTENDED / NO_SHOW once marked - in the order they were requested; {@code waitlist} is WAITLISTED in
 * promotion order. Cancelled bookings are not part of it.
 */
public record SessionRoster(CoachingSession coaching, List<RosterEntry> seats, List<RosterEntry> waitlist) {

    public SessionRoster {
        Objects.requireNonNull(coaching, "coaching must not be null");
        seats = List.copyOf(seats);
        waitlist = List.copyOf(waitlist);
    }

    public SessionId sessionId() {
        return coaching.sessionId();
    }

    public TrainingGroupId trainingGroupId() {
        return coaching.trainingGroupId();
    }

    public MemberId coachId() {
        return coaching.coachId();
    }

    public Instant startsAt() {
        return coaching.startsAt();
    }

    public Instant endsAt() {
        return coaching.endsAt();
    }

    public int capacity() {
        return coaching.capacity();
    }

    public SessionStatus status() {
        return coaching.status();
    }
}
