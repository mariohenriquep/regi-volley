package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.PaymentStatus;

import java.time.LocalDate;
import java.util.Objects;

/**
 * An administrator lists the association's subscriptions in a payment status, e.g. OVERDUE (US-22, RN-18), whose end date lies in a
 * window. Either bound may be null: with neither the window is one year either side of today in Lisbon, with one it is two years from
 * or up to it; a window longer than two years is refused (threat model S1: the list and the CSV stay bounded).
 */
public record ListSubscriptionsByPaymentStatusQuery(Actor actor, PaymentStatus status, LocalDate endingFrom, LocalDate endingTo) {

    public ListSubscriptionsByPaymentStatusQuery {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    /** The default window around today. */
    public ListSubscriptionsByPaymentStatusQuery(Actor actor, PaymentStatus status) {
        this(actor, status, null, null);
    }
}
