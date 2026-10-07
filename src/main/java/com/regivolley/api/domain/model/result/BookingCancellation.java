package com.regivolley.api.domain.model.result;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.CancellationKind;

import java.util.List;

/**
 * Outcome of {@link Session#cancelBooking}: the updated session, the cancelled booking and the
 * waitlisted bookings promoted into the freed seat (RN-09), in promotion order.
 */
public record BookingCancellation(Session session, Booking cancelled, List<Booking> promoted) {

    public BookingCancellation {
        promoted = List.copyOf(promoted);
    }

    /** True when the member cancelled a seat after the free-cancellation deadline (RN-10): the credit stays consumed. */
    public boolean isLate() {
        return cancelled.cancellationKind().filter(kind -> kind == CancellationKind.LATE).isPresent();
    }
}
