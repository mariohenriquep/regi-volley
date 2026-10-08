package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;

import java.util.Objects;

/** Outcome of reversing a payment (RN-19): the new reversal, the subscription with the payment status it now has and what is due again. */
public record PaymentReversed(Payment reversal, Subscription subscription, Money outstanding) {

    public PaymentReversed {
        Objects.requireNonNull(reversal, "reversal must not be null");
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(outstanding, "outstanding must not be null");
    }
}
