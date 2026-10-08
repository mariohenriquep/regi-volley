package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Subscription;

import java.util.Objects;

/** One row of the payment status list (US-22): the subscription and the member's name (personal data, for the administrator only). */
public record SubscriptionPaymentEntry(Subscription subscription, String memberName) {

    public SubscriptionPaymentEntry {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(memberName, "memberName must not be null");
    }
}
