package com.regivolley.api.domain.model.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;

/** Outcome of {@link Session#book}: the updated session and the new booking (CONFIRMED or WAITLISTED). */
public record BookingResult(Session session, Booking booking) {
}
