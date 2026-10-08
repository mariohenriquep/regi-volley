package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.PaymentId;

import java.util.Objects;

/** An administrator reverses a recorded payment (RN-19). */
public record ReversePaymentCommand(Actor actor, PaymentId paymentId) {

    public ReversePaymentCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
    }
}
