package com.regivolley.api.domain.model;

import java.util.List;

/** Outcome of {@link Session#changeCapacity}: the updated session and the bookings promoted into new seats, in order. */
public record CapacityChange(Session session, List<Booking> promoted) {

    public CapacityChange {
        promoted = List.copyOf(promoted);
    }
}
