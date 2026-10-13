package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Payment;

import java.util.Objects;
import java.util.Optional;

/** A payment or reversal with the name of the administrator who recorded it (empty if that member cannot be found). */
public record PaymentLine(Payment payment, Optional<String> recordedByName) {

    public PaymentLine {
        Objects.requireNonNull(payment, "payment must not be null");
        Objects.requireNonNull(recordedByName, "recordedByName must not be null");
    }
}
