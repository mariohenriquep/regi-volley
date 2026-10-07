package com.regivolley.api.domain.model.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;

import java.util.List;

/** Outcome of {@link Session#promoteWaitlist}: the updated session and the bookings promoted, in order. */
public record WaitlistPromotion(Session session, List<Booking> promoted) {

    public WaitlistPromotion {
        promoted = List.copyOf(promoted);
    }
}
