package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The money side of a subscription: the price it was sold at, what has been paid (payments minus reversals) and what is still due, in
 * cents, and every payment and reversal, oldest first. A reversal has {@code reversalOf} set; its amount is positive and counts negatively.
 */
public record SubscriptionPaymentsResponse(UUID subscriptionId, String paymentStatus, long priceCents, long paidCents,
                                           long outstandingCents, List<PaymentLineView> payments) {

    /** One payment or reversal; {@code recordedByName} is left out when that member cannot be found. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentLineView(UUID id, long amountCents, LocalDate paidOn, String method, UUID recordedBy, String recordedByName,
                                  Instant recordedAt, UUID reversalOf) {
    }
}
