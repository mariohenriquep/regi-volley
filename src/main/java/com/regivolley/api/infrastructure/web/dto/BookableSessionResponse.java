package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * A session the caller may book (US-13): when and where it is, when its booking window opens, the seats left, the waitlist length,
 * until when cancelling a confirmed booking is free and, only when the caller has a live booking here, its status, its id (to cancel
 * it) and - while waitlisted - their 1-based place in the queue. {@code freeCancellationUntil} is information: the API decides on
 * submit. Nothing here tells who else booked. Absent properties are left out of the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookableSessionResponse(UUID sessionId, UUID trainingGroupId, String groupName, String venueName, Instant startsAt,
                                      Instant endsAt, Instant bookingOpensAt, Instant freeCancellationUntil, int capacity, int freeSeats,
                                      int waitlistSize, String myBookingStatus, UUID myBookingId, Integer waitlistPosition) {
}
