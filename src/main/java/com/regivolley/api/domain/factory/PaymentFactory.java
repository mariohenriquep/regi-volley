package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.exception.InvalidPaymentException;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Creates and reconstitutes {@link Payment}s, reversals included: the only place a payment is born. {@link Payment}'s
 * constructor checks every invariant, so no path can produce an invalid payment. Stateless, so its methods are static.
 * Whether a payment is <em>allowed</em> (not above what is due, not already reversed) is the {@code PaymentLedger}'s
 * judgement; this builds the payment once it is.
 */
public final class PaymentFactory {

    private PaymentFactory() {
    }

    /**
     * Money received for {@code subscription} (US-21, RN-17), with a generated id, stamped now by the clock.
     *
     * @param paidOn     the day the money was handed over (a Europe/Lisbon calendar date), as the administrator states it
     * @param recordedBy the administrator who records it; the caller has authorised them
     * @throws InvalidFieldException if the amount is zero or the date is after today in Lisbon
     */
    public static Payment create(Subscription subscription, Money amount, LocalDate paidOn, PaymentMethod method,
                                 MemberId recordedBy, Clock clock) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(paidOn, "paidOn must not be null");
        Instant now = clock.instant();
        LocalDate today = now.atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();
        if (paidOn.isAfter(today)) {
            throw new InvalidFieldException("payment date", "The payment date cannot be in the future");
        }
        return new Payment(PaymentId.generate(), subscription.associationId(), subscription.id(), amount, paidOn,
                method, recordedBy, now, null);
    }

    /**
     * The reversal of {@code original} (RN-19): a new payment of the same amount and method that counts negatively,
     * dated today in Lisbon. The original is not touched.
     *
     * @param reversedBy the administrator who reverses it; the caller has authorised them
     * @throws InvalidPaymentException if {@code original} is itself a reversal ({@code PaymentLedger} reports that, and
     *                                 a payment already reversed, as a rule violation before getting here)
     */
    public static Payment createReversal(Payment original, MemberId reversedBy, Clock clock) {
        Objects.requireNonNull(original, "original must not be null");
        Objects.requireNonNull(reversedBy, "reversedBy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (original.isReversal()) {
            throw new InvalidPaymentException("A reversal cannot be reversed");
        }
        Instant now = clock.instant();
        return new Payment(PaymentId.generate(), original.associationId(), original.subscriptionId(), original.amount(),
                now.atZone(ScheduleZone.LISBON.zoneId()).toLocalDate(), original.method(), reversedBy, now,
                original.id());
    }

    /**
     * Rebuilds a payment from persisted data.
     *
     * @param reversalOf the payment this one reverses, or null for an ordinary payment
     */
    public static Payment reconstitute(PaymentId id, AssociationId associationId, SubscriptionId subscriptionId,
                                       Money amount, LocalDate paidOn, PaymentMethod method, MemberId recordedBy,
                                       Instant recordedAt, PaymentId reversalOf) {
        return new Payment(id, associationId, subscriptionId, amount, paidOn, method, recordedBy, recordedAt, reversalOf);
    }
}
