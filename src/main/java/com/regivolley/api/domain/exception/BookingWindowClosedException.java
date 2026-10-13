package com.regivolley.api.domain.exception;

import java.time.Instant;

/** Thrown when a booking is attempted outside the booking window (RN-03). */
public class BookingWindowClosedException extends BusinessRuleException {

    /** Which side of the window was missed. */
    public enum Kind { NOT_YET_OPEN, ALREADY_CLOSED }

    private final Kind kind;
    private final Instant boundary;

    private BookingWindowClosedException(String message, Kind kind, Instant boundary) {
        super(message);
        this.kind = kind;
        this.boundary = boundary;
    }

    public static BookingWindowClosedException notYetOpen(Instant opensAt) {
        return new BookingWindowClosedException(
                "Booking for this session opens at " + LisbonTimeFormat.format(opensAt), Kind.NOT_YET_OPEN, opensAt);
    }

    public static BookingWindowClosedException alreadyClosed(Instant startsAt) {
        return new BookingWindowClosedException(
                "Booking for this session closed at " + LisbonTimeFormat.format(startsAt), Kind.ALREADY_CLOSED, startsAt);
    }

    public Kind kind() {
        return kind;
    }

    /** The instant that was missed: when the window opens, or when it closed (the session start). */
    public Instant boundary() {
        return boundary;
    }
}
