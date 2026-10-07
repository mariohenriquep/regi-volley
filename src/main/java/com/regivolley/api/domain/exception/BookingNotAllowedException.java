package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.BookingRejectionReason;

/** Thrown when a member may not book (RN-06, RN-14, RN-18, RN-21); carries the structured reason. */
public class BookingNotAllowedException extends BusinessRuleException {

    private final BookingRejectionReason reason;

    public BookingNotAllowedException(BookingRejectionReason reason) {
        super(message(reason));
        this.reason = reason;
    }

    private static String message(BookingRejectionReason reason) {
        return switch (reason) {
            case MEMBER_INACTIVE -> "The member is not active";
            case LEVEL_NOT_ALLOWED -> "The member's level is not accepted by this group";
            case PLAN_LEVEL_NOT_ALLOWED -> "The member's plan does not give access to this group's levels";
            case NO_VALID_SUBSCRIPTION -> "The member has no subscription valid on the session date";
            case NO_BALANCE -> "The member's subscription has no balance left for this session";
            case PAYMENT_OVERDUE -> "The member's subscription payment is overdue";
        };
    }

    public BookingRejectionReason reason() {
        return reason;
    }
}
