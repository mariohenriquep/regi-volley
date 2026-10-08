package com.regivolley.api.domain.exception;

/** An invariant of a payment does not hold (e.g. a payment reversing itself): a programming or data error, never shown to users. */
public class InvalidPaymentException extends RuntimeException {

    public InvalidPaymentException(String message) {
        super(message);
    }
}
