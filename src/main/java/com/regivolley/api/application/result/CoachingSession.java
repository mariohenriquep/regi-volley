package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.Optional;

/**
 * One line of the staff's week view: the session, where it is held, how many people hold a seat ({@code confirmedCount}: CONFIRMED,
 * ATTENDED and NO_SHOW bookings), how many wait, how the attendance stands ({@code unmarkedCount} is the seat holders not yet marked)
 * and what the staff may do with it now. The three {@code can...} flags are answered by the {@code Session} aggregate with the
 * clock; they are information for the screen, the use cases decide on submit. The names are left out when the group or the venue
 * cannot be found.
 */
public record CoachingSession(SessionId sessionId, TrainingGroupId trainingGroupId, Optional<String> groupName,
                              Optional<String> venueName, MemberId coachId, Instant startsAt, Instant endsAt, SessionStatus status,
                              int capacity, int confirmedCount, int waitlistCount, int attendedCount, int noShowCount, int unmarkedCount,
                              boolean canMarkAttendance, boolean canCancel, boolean canChangeCapacity) {
}
