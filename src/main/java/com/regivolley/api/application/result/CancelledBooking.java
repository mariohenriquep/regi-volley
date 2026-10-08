package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;

import java.util.List;

/**
 * Outcome of cancelling a booking (US-15): the stored session, the cancelled booking, whether it
 * counted as used because it was after the free-cancellation deadline (RN-10) or the credit went
 * back (RN-15), and the waitlisted bookings promoted into the freed seat, who were charged and notified (RN-09).
 */
public record CancelledBooking(Session session, Booking cancelled, boolean late, boolean creditRefunded,
                               List<Booking> promoted) {

    public CancelledBooking {
        promoted = List.copyOf(promoted);
    }
}
