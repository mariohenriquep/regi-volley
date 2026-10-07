package com.regivolley.api.domain.model;

/** Outcome of {@link Session#book}: the updated session and the new booking (CONFIRMED or WAITLISTED). */
public record BookingResult(Session session, Booking booking) {
}
