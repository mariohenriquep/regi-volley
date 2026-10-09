package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The caller's current and upcoming subscriptions with what is left on each (US-23); {@code remaining} is absent for unlimited plans. */
public record MyPlanResponse(LocalDate asOf, List<SubscriptionView> subscriptions) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubscriptionView(UUID subscriptionId, String planName, String type, LocalDate startDate, LocalDate endDate,
                               String paymentStatus, Integer remaining) {
    }
}
