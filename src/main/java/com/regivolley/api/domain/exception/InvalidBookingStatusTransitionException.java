package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.BookingStatus;

/** Thrown when a {@code Booking} is asked to move to a status not reachable from its current one (RN-12). */
public class InvalidBookingStatusTransitionException extends BusinessRuleException {

    private final BookingStatus from;
    private final BookingStatus to;

    public InvalidBookingStatusTransitionException(BookingStatus from, BookingStatus to) {
        super("Cannot move the booking from " + label(from) + " to " + label(to));
        this.from = from;
        this.to = to;
    }

    private static String label(BookingStatus status) {
        return switch (status) {
            case WAITLISTED -> "waitlisted";
            case CONFIRMED -> "confirmed";
            case ATTENDED -> "attended";
            case NO_SHOW -> "no-show";
            case CANCELLED -> "cancelled";
        };
    }

    public BookingStatus from() {
        return from;
    }

    public BookingStatus to() {
        return to;
    }
}
