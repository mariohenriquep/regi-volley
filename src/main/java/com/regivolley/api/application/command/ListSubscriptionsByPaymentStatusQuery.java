package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.PaymentStatus;

import java.util.Objects;

/** An administrator lists the association's subscriptions in a payment status, e.g. OVERDUE (US-22, RN-18). */
public record ListSubscriptionsByPaymentStatusQuery(Actor actor, PaymentStatus status) {

    public ListSubscriptionsByPaymentStatusQuery {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
