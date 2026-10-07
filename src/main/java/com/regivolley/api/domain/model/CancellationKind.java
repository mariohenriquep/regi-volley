package com.regivolley.api.domain.model;

/**
 * Why a booking ended up CANCELLED. Later slices use it (together with
 * {@link Booking#consumedCredit()}) to decide the credit outcome: FREE gives the credit back
 * (RN-10), LATE keeps it consumed (RN-10), BY_SESSION refunds it and notifies the member (RN-04).
 */
public enum CancellationKind {
    /** Cancelled by the member up to the free-cancellation deadline, or while only waitlisted. */
    FREE,
    /** Cancelled by the member, holding a seat, after the free-cancellation deadline. */
    LATE,
    /** Cancelled as a consequence of the whole session being cancelled (RN-04). */
    BY_SESSION,
    /** Still waitlisted when the session was completed: never got a seat, so nothing was consumed. */
    NOT_PROMOTED
}
