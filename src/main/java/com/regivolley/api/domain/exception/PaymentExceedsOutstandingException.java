package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.Money;

/**
 * Thrown when a payment is larger than what is still due on the subscription (nothing at all once it is
 * settled). Partial payments are allowed, overpayments are not, so the recorded total never exceeds the price.
 */
public class PaymentExceedsOutstandingException extends BusinessRuleException {

    private final Money outstanding;

    public PaymentExceedsOutstandingException(Money outstanding) {
        super("The amount is more than what is still due on this subscription (" + outstanding + ")");
        this.outstanding = outstanding;
    }

    public Money outstanding() {
        return outstanding;
    }
}
