package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

/**
 * Why a member may not book (RN-06, RN-14, RN-18, RN-21). Each reason has an explicit
 * {@link #priority()}: when several subscriptions are rejected for different reasons, the one
 * with the highest priority (closest to bookable) is reported, so a member with an overdue
 * subscription is told to pay rather than that some old plan expired. The priority is
 * deliberately independent of declaration order.
 */
public enum BookingRejectionReason implements ValueObject {
    MEMBER_INACTIVE(0),
    LEVEL_NOT_ALLOWED(1),
    NO_VALID_SUBSCRIPTION(2),
    PLAN_LEVEL_NOT_ALLOWED(3),
    NO_BALANCE(4),
    PAYMENT_OVERDUE(5);

    private final int priority;

    BookingRejectionReason(int priority) {
        this.priority = priority;
    }

    /** Higher means closer to bookable. */
    public int priority() {
        return priority;
    }

    /** Whether this reason is closer to bookable than {@code other}. */
    public boolean isCloserToBookableThan(BookingRejectionReason other) {
        return priority > other.priority;
    }
}
