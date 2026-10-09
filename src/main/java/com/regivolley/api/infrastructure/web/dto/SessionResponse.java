package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A session as its staff (the coach or an administrator) see it, with its bookings. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionResponse(UUID id, UUID trainingGroupId, UUID coachId, Instant startsAt, Instant endsAt, int capacity, String status,
                              int confirmedCount, int freeSeats, int waitlistSize, String cancellationReason, List<BookingView> bookings) {

    public record BookingView(UUID id, UUID memberId, String status) {
    }
}
