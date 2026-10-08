package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;

import java.util.Objects;

/** Outcome of recording a payment (US-21): the stored payment, the subscription with the payment status it now has and what is still due (zero once paid in full). */
public record PaymentRecorded(Payment payment, Subscription subscription, Money outstanding) {

    public PaymentRecorded {
        Objects.requireNonNull(payment, "payment must not be null");
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(outstanding, "outstanding must not be null");
    }
}
