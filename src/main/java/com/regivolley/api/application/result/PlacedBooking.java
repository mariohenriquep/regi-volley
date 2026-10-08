package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;

import java.util.OptionalInt;

/**
 * Outcome of booking a session (US-14): the stored session, the new booking (CONFIRMED, or
 * WAITLISTED when the session was full) and, for a waitlisted one, the 1-based place in the queue.
 */
public record PlacedBooking(Session session, Booking booking, OptionalInt waitlistPosition) {
}
