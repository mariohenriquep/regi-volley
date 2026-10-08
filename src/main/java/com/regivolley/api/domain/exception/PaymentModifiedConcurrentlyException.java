package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.PaymentId;

/**
 * A payment could not be stored because the database refused it: the same payment was reversed by a concurrent
 * request, or the id already exists. Payments are append-only, so there is nothing to merge; nothing was stored.
 * The use case retries in a new transaction and then sees the payment as it now is.
 */
public class PaymentModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final PaymentId paymentId;

    public PaymentModifiedConcurrentlyException(PaymentId paymentId) {
        super("payment", paymentId.value());
        this.paymentId = paymentId;
    }

    public PaymentId paymentId() {
        return paymentId;
    }
}
