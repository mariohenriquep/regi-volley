package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/** RN-12: WAITLISTED -> CONFIRMED -> ATTENDED | NO_SHOW, and WAITLISTED | CONFIRMED -> CANCELLED. */
public enum BookingStatus implements ValueObject {
    WAITLISTED,
    CONFIRMED,
    ATTENDED,
    NO_SHOW,
    CANCELLED;

    /** An active booking still occupies a place in the session (a seat or a waitlist slot) - RN-07. */
    public boolean isActive() {
        return this == WAITLISTED || this == CONFIRMED;
    }

    /** The booking took a seat in the session and kept it: CONFIRMED, and ATTENDED / NO_SHOW once marked (not WAITLISTED, not CANCELLED). */
    public boolean holdsSeat() {
        return this == CONFIRMED || this == ATTENDED || this == NO_SHOW;
    }
}
