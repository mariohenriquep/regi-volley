package com.regivolley.api.domain.exception;

/**
 * Thrown when a member already has a non-cancelled booking in the session (RN-07). Only a
 * cancelled booking allows booking again; ATTENDED and NO_SHOW bookings block it too.
 */
public class DuplicateBookingException extends BusinessRuleException {

    public DuplicateBookingException() {
        super("The member already has a booking in this session");
    }
}
