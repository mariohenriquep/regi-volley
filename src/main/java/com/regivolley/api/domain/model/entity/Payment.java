package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidPaymentException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.shared.AggregateRoot;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Money received for a {@link Subscription} (US-21, RN-17), recorded by hand by an administrator. Immutable
 * and <b>append-only</b> (RN-19): a payment is never updated or deleted, so it has no version to
 * protect; a mistake is corrected by recording a <em>reversal</em>, a second payment of the same amount
 * that points at the first through {@link #reversalOf()} and counts negatively. The audit (who recorded it and
 * when) is part of every payment, reversals included.
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

    private Payment(PaymentId id, AssociationId associationId, SubscriptionId subscriptionId, Money amount,
                    LocalDate paidOn, PaymentMethod method, MemberId recordedBy, Instant recordedAt,
                    PaymentId reversalOf) {
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

    /**
     * Records money received for {@code subscription}.
     *
     * @param paidOn     the day the money was handed over (a Europe/Lisbon calendar date), as the administrator states it
     * @param recordedBy the administrator who records it; the caller has authorised them
     * @throws InvalidFieldException if the amount is zero or the date is after today in Lisbon
     */
    public static Payment record(Subscription subscription, Money amount, LocalDate paidOn, PaymentMethod method,
                                 MemberId recordedBy, Clock clock) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(paidOn, "paidOn must not be null");
        LocalDate today = clock.instant().atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();
        if (paidOn.isAfter(today)) {
            throw new InvalidFieldException("payment date", "The payment date cannot be in the future");
        }
        return reconstruct(PaymentId.generate(), subscription.associationId(), subscription.id(), amount, paidOn,
                method, recordedBy, clock.instant(), null);
    }

    /**
     * Rebuilds a payment from persisted data, re-checking its invariants.
     *
     * @param reversalOf the payment this one reverses, or null for an ordinary payment
     */
    public static Payment reconstruct(PaymentId id, AssociationId associationId, SubscriptionId subscriptionId,
                                      Money amount, LocalDate paidOn, PaymentMethod method, MemberId recordedBy,
                                      Instant recordedAt, PaymentId reversalOf) {
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
        return new Payment(id, associationId, subscriptionId, amount, paidOn, method, recordedBy, recordedAt, reversalOf);
    }

    /**
     * The reversal of this payment (RN-19): a new payment of the same amount and method that counts negatively,
     * dated today in Lisbon. This payment is not touched.
     *
     * @param reversedBy the administrator who reverses it; the caller has authorised them
     * @throws InvalidPaymentException if this payment is itself a reversal ({@code PaymentLedger} reports that,
     *                                 and a payment already reversed, as a rule violation before getting here)
     */
    public Payment reverse(MemberId reversedBy, Clock clock) {
        Objects.requireNonNull(reversedBy, "reversedBy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (isReversal()) {
            throw new InvalidPaymentException("A reversal cannot be reversed");
        }
        Instant now = clock.instant();
        return reconstruct(PaymentId.generate(), associationId, subscriptionId, amount,
                now.atZone(ScheduleZone.LISBON.zoneId()).toLocalDate(), method, reversedBy, now, id);
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
