package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * A session the caller may book (US-13): when it is, when its booking window opens, the seats left, the waitlist length and the
 * caller's own booking status if they have one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookableSessionResponse(UUID sessionId, UUID trainingGroupId, String groupName, Instant startsAt, Instant endsAt,
                                      Instant bookingOpensAt, int capacity, int freeSeats, int waitlistSize, String myBookingStatus) {
}
