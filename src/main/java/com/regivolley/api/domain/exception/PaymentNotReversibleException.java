package com.regivolley.api.domain.exception;

/** Thrown when a payment cannot be reversed: it already was, or it is itself a reversal (RN-19). */
public class PaymentNotReversibleException extends BusinessRuleException {

    private PaymentNotReversibleException(String message) {
        super(message);
    }

    public static PaymentNotReversibleException alreadyReversed() {
        return new PaymentNotReversibleException("This payment was already reversed");
    }

    public static PaymentNotReversibleException isAReversal() {
        return new PaymentNotReversibleException("A reversal cannot be reversed; record a new payment instead");
    }
}
