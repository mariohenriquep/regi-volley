package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.util.Objects;

/** An administrator marks an unpaid subscription as overdue (RN-18): its member can no longer book until it is paid. */
public record MarkSubscriptionOverdueCommand(Actor actor, SubscriptionId subscriptionId) {

    public MarkSubscriptionOverdueCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
    }
}
