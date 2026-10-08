package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.PaymentId;

/** Thrown when a payment id doesn't exist in the association it is addressed to (maps to "not found"). */
public class PaymentNotFoundException extends RuntimeException {

    private final PaymentId paymentId;

    public PaymentNotFoundException(PaymentId paymentId) {
        super("Payment not found in this association: " + paymentId);
        this.paymentId = paymentId;
    }

    public PaymentId paymentId() {
        return paymentId;
    }
}
