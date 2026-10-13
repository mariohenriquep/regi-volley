package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;

import java.util.Objects;
import java.util.Optional;

/**
 * One of a member's subscriptions as the administrator sees it: the plan's name (empty if the plan cannot be found), what has been
 * paid and what is still due, both decided by the payment ledger from the subscription's own price.
 */
public record MemberSubscriptionEntry(Subscription subscription, Optional<String> planName, Money paid, Money outstanding) {

    public MemberSubscriptionEntry {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(planName, "planName must not be null");
        Objects.requireNonNull(paid, "paid must not be null");
        Objects.requireNonNull(outstanding, "outstanding must not be null");
    }
}
