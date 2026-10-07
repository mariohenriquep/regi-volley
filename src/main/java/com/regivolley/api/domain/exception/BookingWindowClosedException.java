package com.regivolley.api.domain.exception;

import java.time.Instant;

/** Thrown when a booking is attempted outside the booking window (RN-03). */
public class BookingWindowClosedException extends BusinessRuleException {

    private final Instant boundary;

    private BookingWindowClosedException(String message, Instant boundary) {
        super(message);
        this.boundary = boundary;
    }

    public static BookingWindowClosedException notYetOpen(Instant opensAt) {
        return new BookingWindowClosedException(
                "Booking for this session opens at " + LisbonTimeFormat.format(opensAt), opensAt);
    }

    public static BookingWindowClosedException alreadyClosed(Instant startsAt) {
        return new BookingWindowClosedException(
                "Booking for this session closed at " + LisbonTimeFormat.format(startsAt), startsAt);
    }

    /** The instant that was missed: when the window opens, or when it closed (the session start). */
    public Instant boundary() {
        return boundary;
    }
}
