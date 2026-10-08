package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.time.LocalDate;
import java.util.Objects;
import java.util.OptionalInt;

/** One of a member's subscriptions as US-23 shows it. {@code remaining} is the credits left for a pack or single session, the sessions left this week for a weekly plan and empty for an unlimited one. */
public record MySubscription(SubscriptionId subscriptionId, String planName, PlanType type, LocalDate startDate, LocalDate endDate, PaymentStatus paymentStatus, OptionalInt remaining) {

    public MySubscription {
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
        Objects.requireNonNull(planName, "planName must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(endDate, "endDate must not be null");
        Objects.requireNonNull(paymentStatus, "paymentStatus must not be null");
        Objects.requireNonNull(remaining, "remaining must not be null");
    }
}
