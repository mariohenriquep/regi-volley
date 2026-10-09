package com.regivolley.api.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Who is in a session, for its staff (US-17): the seats ({@code seats}: CONFIRMED, then ATTENDED / NO_SHOW once marked) and the
 * waitlist in promotion order, each with the booking id that attendance is marked with. A person is a member id and a display name,
 * nothing else - no email, no phone.
 */
public record SessionRosterResponse(UUID sessionId, UUID trainingGroupId, UUID coachId, Instant startsAt, Instant endsAt, int capacity,
                                    String status, List<EntryView> seats, List<EntryView> waitlist) {

    public record EntryView(UUID bookingId, UUID memberId, String memberName, String status) {
    }
}
