package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * A session in the staff's week (US-11, US-12, US-17): when and where it is, its status, how many people hold a seat
 * ({@code confirmedCount}: confirmed, attended and no-show bookings), how many wait, how the attendance stands
 * ({@code unmarkedCount}: seat holders not marked yet) and what the staff may do with it now - {@code canMarkAttendance},
 * {@code canCancel}, {@code canChangeCapacity}, answered by the session. The flags are information for the screen: the API decides
 * on submit. {@code groupName} and {@code venueName} are left out when the group or the venue cannot be found.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CoachingSessionResponse(UUID sessionId, UUID trainingGroupId, String groupName, String venueName, UUID coachId,
                                      Instant startsAt, Instant endsAt, String status, int capacity, int confirmedCount,
                                      int waitlistCount, int attendedCount, int noShowCount, int unmarkedCount,
                                      boolean canMarkAttendance, boolean canCancel, boolean canChangeCapacity) {
}
