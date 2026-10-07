package com.regivolley.api.domain.model;

/** RN-12: WAITLISTED -> CONFIRMED -> ATTENDED | NO_SHOW, and WAITLISTED | CONFIRMED -> CANCELLED. */
public enum BookingStatus {
    WAITLISTED,
    CONFIRMED,
    ATTENDED,
    NO_SHOW,
    CANCELLED;

    /** An active booking still occupies a place in the session (a seat or a waitlist slot) - RN-07. */
    public boolean isActive() {
        return this == WAITLISTED || this == CONFIRMED;
    }
}
