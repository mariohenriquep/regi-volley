package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.SessionId;

import java.util.Objects;

/**
 * Cancels a booking (US-15). The actor must own the booking, coach the session or be an administrator.
 */
public record CancelBookingCommand(Actor actor, SessionId sessionId, BookingId bookingId) {

    public CancelBookingCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
    }
}
