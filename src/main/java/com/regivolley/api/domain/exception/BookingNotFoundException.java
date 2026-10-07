package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.BookingId;

/** Thrown when a booking id doesn't belong to the session it is addressed to (not a user rule: maps to "not found"). */
public class BookingNotFoundException extends RuntimeException {

    private final BookingId bookingId;

    public BookingNotFoundException(BookingId bookingId) {
        super("Booking not found in this session: " + bookingId);
        this.bookingId = bookingId;
    }

    public BookingId bookingId() {
        return bookingId;
    }
}
