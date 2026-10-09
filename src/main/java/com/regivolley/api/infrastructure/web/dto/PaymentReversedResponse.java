package com.regivolley.api.infrastructure.web.dto;

/** The reversal that was recorded, the subscription's payment status afterwards and what is owed again, in cents. */
public record PaymentReversedResponse(PaymentResponse reversal, String paymentStatus, long outstandingCents) {
}
