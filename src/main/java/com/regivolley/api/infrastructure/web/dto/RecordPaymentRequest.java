package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/** Money received for the subscription in the path (US-21, RN-17): the amount in cents, the day it was paid and how. */
public record RecordPaymentRequest(
        @NotNull @Positive @Max(100_000_000) Long amountCents,
        @NotNull @PlausibleDate LocalDate paidOn,
        @NotNull PaymentMethodName method) {
}
