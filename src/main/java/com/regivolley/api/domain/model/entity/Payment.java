package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidPaymentException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.shared.AggregateRoot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Money received for a {@link Subscription} (US-21, RN-17), recorded by hand by an administrator. Immutable
 * and <b>append-only</b> (RN-19): a payment is never updated or deleted, so it has no version to
 * protect; a mistake is corrected by recording a <em>reversal</em>, a second payment of the same amount
 * that points at the first through {@link #reversalOf()} and counts negatively. The audit (who recorded it and
 * when) is part of every payment, reversals included. A payment, and its reversal, is created and reconstituted
 * only by {@code PaymentFactory}.
 *
 * <p>The amount is always positive; {@link #signedCents()} gives the contribution to what the member has paid.
 * What the payments of a subscription add up to, and whether that settles the subscription, is judged by
 * {@code PaymentLedger}. {@link #toString()} prints ids only.
 */
public final class Payment implements AggregateRoot {

    private final PaymentId id;
    private final AssociationId associationId;
    private final SubscriptionId subscriptionId;
    private final Money amount;
    private final LocalDate paidOn;
    private final PaymentMethod method;
    private final MemberId recordedBy;
    private final Instant recordedAt;
    private final PaymentId reversalOf;

    /**
     * Checks every invariant, so no payment exists in an invalid state. Public because the only callers are
     * {@code PaymentFactory} (new payments, reversals and persisted ones) and this class; the architecture test
     * pins that.
     *
     * @param reversalOf the payment this one reverses, or null for an ordinary payment
     */
    public Payment(PaymentId id, AssociationId associationId, SubscriptionId subscriptionId, Money amount,
                   LocalDate paidOn, PaymentMethod method, MemberId recordedBy, Instant recordedAt,
                   PaymentId reversalOf) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(paidOn, "paidOn must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(recordedBy, "recordedBy must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        if (amount.cents() == 0) {
            throw new InvalidFieldException("amount", "The payment amount must be greater than zero");
        }
        if (id.equals(reversalOf)) {
            throw new InvalidPaymentException("A payment cannot reverse itself");
        }
        this.id = id;
        this.associationId = associationId;
        this.subscriptionId = subscriptionId;
        this.amount = amount;
        this.paidOn = paidOn;
        this.method = method;
        this.recordedBy = recordedBy;
        this.recordedAt = recordedAt;
        this.reversalOf = reversalOf;
    }

    public boolean isReversal() {
        return reversalOf != null;
    }

    /** Its effect on what the member has paid: positive for a payment, negative for a reversal. */
    public long signedCents() {
        return isReversal() ? -amount.cents() : amount.cents();
    }

    public PaymentId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public SubscriptionId subscriptionId() {
        return subscriptionId;
    }

    /** Always positive, also for a reversal. */
    public Money amount() {
        return amount;
    }

    public LocalDate paidOn() {
        return paidOn;
    }

    public PaymentMethod method() {
        return method;
    }

    /** The administrator who recorded it. */
    public MemberId recordedBy() {
        return recordedBy;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    /** The payment this one reverses; empty for an ordinary payment. */
    public Optional<PaymentId> reversalOf() {
        return Optional.ofNullable(reversalOf);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Payment{id=%s, subscriptionId=%s}".formatted(id, subscriptionId);
    }
}
