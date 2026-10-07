package com.regivolley.api.domain.model;

import java.util.List;

/** Outcome of {@link Session#promoteWaitlist}: the updated session and the bookings promoted, in order. */
public record WaitlistPromotion(Session session, List<Booking> promoted) {

    public WaitlistPromotion {
        promoted = List.copyOf(promoted);
    }
}
