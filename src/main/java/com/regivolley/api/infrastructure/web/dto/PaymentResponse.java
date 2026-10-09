package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A payment, or a reversal of one ({@code reversalOf} set; the amount is positive and counts negatively). In cents. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(UUID id, UUID subscriptionId, long amountCents, LocalDate paidOn, String method, UUID recordedBy,
                              Instant recordedAt, UUID reversalOf) {
}
