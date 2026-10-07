package com.regivolley.api.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An exact, non-negative amount in euro, held as whole cents so no floating-point or scale
 * surprises can creep into prices and payments. Single currency by design: the product targets
 * Portuguese associations; a currency field is a deliberate future change.
 */
public record Money(long cents) {

    public Money {
        if (cents < 0) {
            throw new IllegalArgumentException("Money must not be negative");
        }
    }

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /** From a euro amount with at most two decimals; more decimals are rejected rather than rounded. */
    public static Money ofEuros(BigDecimal euros) {
        Objects.requireNonNull(euros, "euros must not be null");
        try {
            return new Money(euros.movePointRight(2).longValueExact());
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Money must have at most two decimals and fit in a long: " + euros, e);
        }
    }

    /** The amount in euro, always with two decimals. */
    public BigDecimal euros() {
        return BigDecimal.valueOf(cents, 2);
    }

    @Override
    public String toString() {
        return euros().toPlainString() + " EUR";
    }
}
