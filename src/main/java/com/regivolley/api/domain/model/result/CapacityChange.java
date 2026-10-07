package com.regivolley.api.domain.model.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;

import java.util.List;

/** Outcome of {@link Session#changeCapacity}: the updated session and the bookings promoted into new seats, in order. */
public record CapacityChange(Session session, List<Booking> promoted) {

    public CapacityChange {
        promoted = List.copyOf(promoted);
    }
}
