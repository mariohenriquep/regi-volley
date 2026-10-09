package com.regivolley.api.infrastructure.web.dto;

/** The recorded payment, the subscription's payment status afterwards and what is still owed, in cents. */
public record PaymentRecordedResponse(PaymentResponse payment, String paymentStatus, long outstandingCents) {
}
