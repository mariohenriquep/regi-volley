package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;

import java.util.List;

/**
 * Outcome of cancelling a session (US-11, RN-04): the stored session, every booking it cancelled
 * (whose members were notified) and how many credits went back to their subscriptions.
 */
public record CancelledSession(Session session, List<Booking> cancelledBookings, int creditsRefunded) {

    public CancelledSession {
        cancelledBookings = List.copyOf(cancelledBookings);
    }
}
