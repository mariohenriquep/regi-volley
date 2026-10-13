package com.regivolley.api.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Who is in a session, for its staff (US-17): the seats ({@code seats}: CONFIRMED, then ATTENDED / NO_SHOW once marked) and the
 * waitlist in promotion order, each with the booking id that attendance is marked with. A person is a member id and a display name,
 * nothing else - no email, no phone. {@code coaching} is the same line the staff's week lists for this session (names, counts and the
 * action flags {@code canMarkAttendance}, {@code canCancel}, {@code canChangeCapacity}), so a screen needs no second request; the
 * flags are information, the operations decide again on submit.
 */
public record SessionRosterResponse(UUID sessionId, UUID trainingGroupId, UUID coachId, Instant startsAt, Instant endsAt, int capacity,
                                    String status, CoachingSessionResponse coaching, List<RosterEntryView> seats,
                                    List<RosterEntryView> waitlist) {

    public record RosterEntryView(UUID bookingId, UUID memberId, String memberName, String status) {
    }
}
