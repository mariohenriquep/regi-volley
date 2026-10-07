package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;

import java.util.List;

/** Outcome of changing a session's capacity (US-12): the stored session and the waitlisted bookings promoted into new seats, in order. */
public record CapacityChanged(Session session, List<Booking> promoted) {

    public CapacityChanged {
        promoted = List.copyOf(promoted);
    }
}
