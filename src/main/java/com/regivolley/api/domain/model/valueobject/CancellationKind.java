package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/**
 * Why a booking ended up CANCELLED. Later slices use it (together with
 * {@code Booking#consumedCredit()}) to decide the credit outcome: FREE gives the credit back
 * (RN-10), LATE keeps it consumed (RN-10), BY_SESSION refunds it and notifies the member (RN-04).
 */
public enum CancellationKind implements ValueObject {
    /** Cancelled by the member up to the free-cancellation deadline, or while only waitlisted. */
    FREE,
    /** Cancelled by the member, holding a seat, after the free-cancellation deadline. */
    LATE,
    /** Cancelled as a consequence of the whole session being cancelled (RN-04). */
    BY_SESSION,
    /** Cancelled by the association, not the member (deactivating the member, US-08): never late, refunded like BY_SESSION. */
    BY_ASSOCIATION,
    /** Still waitlisted when the session was completed: never got a seat, so nothing was consumed. */
    NOT_PROMOTED
}
