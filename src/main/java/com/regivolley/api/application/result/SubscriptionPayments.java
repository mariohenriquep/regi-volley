package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;

import java.util.List;
import java.util.Objects;

/**
 * The money side of one subscription (US-21, RN-17, RN-19): its payments and reversals, oldest first, and the totals the payment
 * ledger derives from them and from the subscription's own price.
 */
public record SubscriptionPayments(Subscription subscription, List<PaymentLine> payments, Money paid, Money outstanding) {

    public SubscriptionPayments {
        Objects.requireNonNull(subscription, "subscription must not be null");
        payments = List.copyOf(Objects.requireNonNull(payments, "payments must not be null"));
        Objects.requireNonNull(paid, "paid must not be null");
        Objects.requireNonNull(outstanding, "outstanding must not be null");
    }
}
