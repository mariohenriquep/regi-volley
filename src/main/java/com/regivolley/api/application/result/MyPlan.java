package com.regivolley.api.application.result;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** A member's current and upcoming subscriptions as of a Lisbon calendar day, earliest first (US-23). */
public record MyPlan(LocalDate asOf, List<MySubscription> subscriptions) {

    public MyPlan {
        Objects.requireNonNull(asOf, "asOf must not be null");
        Objects.requireNonNull(subscriptions, "subscriptions must not be null");
    }
}
