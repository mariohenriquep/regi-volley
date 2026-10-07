package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.PaymentStatus;

/** Thrown when a {@code Subscription} is asked to move to a payment status not reachable from its current one (RN-18). */
public class InvalidPaymentStatusTransitionException extends BusinessRuleException {

    private final PaymentStatus from;
    private final PaymentStatus to;

    public InvalidPaymentStatusTransitionException(PaymentStatus from, PaymentStatus to) {
        super("Cannot move the subscription payment from " + label(from) + " to " + label(to));
        this.from = from;
        this.to = to;
    }

    private static String label(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case PAID -> "paid";
            case OVERDUE -> "overdue";
        };
    }

    public PaymentStatus from() {
        return from;
    }

    public PaymentStatus to() {
        return to;
    }
}
