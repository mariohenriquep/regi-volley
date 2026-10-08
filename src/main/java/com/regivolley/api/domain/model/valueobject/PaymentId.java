package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.shared.ValueObject;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier, so ids of different aggregates can't be mixed up at compile time. */
public record PaymentId(UUID value) implements ValueObject {

    public PaymentId {
        Objects.requireNonNull(value, "PaymentId value must not be null");
    }

    public static PaymentId generate() {
        return new PaymentId(UUID.randomUUID());
    }

    public static PaymentId of(UUID value) {
        return new PaymentId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
