package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.util.Objects;

/** An administrator reads the payments and reversals of one subscription of their association (US-21, RN-17, RN-19). */
public record ListSubscriptionPaymentsQuery(Actor actor, SubscriptionId subscriptionId) {

    public ListSubscriptionPaymentsQuery {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
    }
}
