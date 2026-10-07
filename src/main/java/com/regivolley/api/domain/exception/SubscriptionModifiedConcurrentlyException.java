package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.SubscriptionId;

/**
 * A subscription (its balance and credit usages) was changed by a concurrent request between load
 * and save, so this write was rejected and nothing was stored. This is what stops two simultaneous
 * bookings of one member from spending the same last credit: the booking use case reloads the
 * subscription and decides again against the up-to-date balance.
 */
public class SubscriptionModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final SubscriptionId subscriptionId;

    public SubscriptionModifiedConcurrentlyException(SubscriptionId subscriptionId) {
        super("subscription", subscriptionId.value());
        this.subscriptionId = subscriptionId;
    }

    public SubscriptionId subscriptionId() {
        return subscriptionId;
    }
}
