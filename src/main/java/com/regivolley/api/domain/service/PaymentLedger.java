package com.regivolley.api.domain.service;

import com.regivolley.api.domain.exception.PaymentExceedsOutstandingException;
import com.regivolley.api.domain.exception.PaymentNotReversibleException;
import com.regivolley.api.domain.factory.PaymentFactory;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The payments of one subscription against the price it was sold at (US-21, RN-17, RN-19, RN-18): what has been paid,
 * what is still due, whether a new payment or a reversal is allowed, and the payment status the subscription
 * must have as a result. Pure and immutable: the use case loads the subscription, its price and its payments,
 * asks, then stores the new payment and the settled subscription.
 *
 * <p><b>Partial payments</b> are allowed (a member may pay a pack in two instalments); an overpayment is not,
 * so the paid total never exceeds the price. The subscription is PAID exactly when nothing is due. Paid is
 * the sum of the payments minus the sum of their reversals. It decides; the payment it hands back is built by
 * {@code PaymentFactory}.
 */
public final class PaymentLedger {

    private final Subscription subscription;
    private final long priceCents;
    private final List<Payment> payments;

    private PaymentLedger(Subscription subscription, long priceCents, List<Payment> payments) {
        this.subscription = subscription;
        this.priceCents = priceCents;
        this.payments = payments;
    }

    /**
     * @param payments all the payments and reversals recorded for {@code subscription}
     * @throws IllegalArgumentException if one of them belongs to another subscription or association
     */
    public static PaymentLedger of(Subscription subscription, Collection<Payment> payments) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(payments, "payments must not be null");
        if (payments.stream().anyMatch(p -> !p.subscriptionId().equals(subscription.id())
                || !p.associationId().equals(subscription.associationId()))) {
            throw new IllegalArgumentException("A payment of another subscription cannot be part of this ledger");
        }
        return new PaymentLedger(subscription, subscription.price().cents(), List.copyOf(payments));
    }

    /** What the member has paid so far: payments minus reversals. */
    public Money paid() {
        return Money.ofCents(Math.max(0, payments.stream().mapToLong(Payment::signedCents).sum()));
    }

    /** What is still due; zero once settled (or if the price was lowered below what was paid). */
    public Money outstanding() {
        return Money.ofCents(Math.max(0, priceCents - paid().cents()));
    }

    public boolean isSettled() {
        return outstanding().cents() == 0;
    }

    /**
     * A new payment for this subscription.
     *
     * @throws PaymentExceedsOutstandingException if the amount is more than what is due
     * @throws com.regivolley.api.domain.exception.InvalidFieldException if the amount is zero
     */
    public Payment record(Money amount, LocalDate paidOn, PaymentMethod method, MemberId recordedBy, Clock clock) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.cents() > outstanding().cents()) {
            throw new PaymentExceedsOutstandingException(outstanding());
        }
        return PaymentFactory.create(subscription, amount, paidOn, method, recordedBy, clock);
    }

    /**
     * The reversal of one of this subscription's payments (RN-19).
     *
     * @throws PaymentNotReversibleException if it is a reversal itself, or was already reversed
     * @throws IllegalArgumentException      if the payment is not one of this ledger's
     */
    public Payment reverse(PaymentId paymentId, MemberId reversedBy, Clock clock) {
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Payment payment = payments.stream().filter(p -> p.id().equals(paymentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The payment is not one of this subscription's"));
        if (payment.isReversal()) {
            throw PaymentNotReversibleException.isAReversal();
        }
        Set<PaymentId> reversed = new HashSet<>();
        payments.forEach(p -> p.reversalOf().ifPresent(reversed::add));
        if (reversed.contains(paymentId)) {
            throw PaymentNotReversibleException.alreadyReversed();
        }
        return PaymentFactory.createReversal(payment, reversedBy, clock);
    }

    /** The ledger with one more payment, the one just recorded or reversed. */
    public PaymentLedger with(Payment payment) {
        Objects.requireNonNull(payment, "payment must not be null");
        List<Payment> all = new ArrayList<>(payments);
        all.add(payment);
        return of(subscription, all);
    }

    /**
     * The subscription with the payment status this ledger calls for: PAID when settled (from PENDING or
     * OVERDUE), back to PENDING when it was PAID but money is due again after a reversal; otherwise unchanged
     * (an OVERDUE subscription stays overdue until paid in full).
     *
     * @throws IllegalArgumentException if {@code current} is not the ledger's subscription
     */
    public Subscription settle(Subscription current) {
        Objects.requireNonNull(current, "current must not be null");
        if (!current.id().equals(subscription.id())) {
            throw new IllegalArgumentException("The ledger belongs to another subscription");
        }
        boolean paid = current.paymentStatus() == PaymentStatus.PAID;
        if (isSettled() && !paid) {
            return current.markPaid();
        }
        if (!isSettled() && paid) {
            return current.reopenPayment();
        }
        return current;
    }
}
