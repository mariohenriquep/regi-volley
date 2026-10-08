package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.time.LocalDate;
import java.util.Objects;

/** An administrator records money received for a subscription (US-21, RN-17). */
public record RecordPaymentCommand(Actor actor, SubscriptionId subscriptionId, Money amount, LocalDate paidOn, PaymentMethod method) {

    public RecordPaymentCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(paidOn, "paidOn must not be null");
        Objects.requireNonNull(method, "method must not be null");
    }
}
