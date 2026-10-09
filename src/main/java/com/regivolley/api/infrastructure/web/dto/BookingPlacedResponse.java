package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** The caller's new booking: {@code CONFIRMED}, or {@code WAITLISTED} with the place in the queue (RN-08). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingPlacedResponse(UUID sessionId, UUID bookingId, String status, Integer waitlistPosition) {
}
