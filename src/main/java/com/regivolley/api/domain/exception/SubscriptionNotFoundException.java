package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.SubscriptionId;

/** Thrown when a subscription id doesn't exist in the association it is addressed to (maps to "not found"). */
public class SubscriptionNotFoundException extends RuntimeException {

    private final SubscriptionId subscriptionId;

    public SubscriptionNotFoundException(SubscriptionId subscriptionId) {
        super("Subscription not found in this association: " + subscriptionId);
        this.subscriptionId = subscriptionId;
    }

    public SubscriptionId subscriptionId() {
        return subscriptionId;
    }
}
