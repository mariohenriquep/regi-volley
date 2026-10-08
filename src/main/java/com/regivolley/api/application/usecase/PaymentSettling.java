package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.domain.exception.SubscriptionNotFoundException;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.service.PaymentLedger;

import java.util.List;
import java.util.function.Function;

/**
 * What recording and reversing a payment have in common (US-21, RN-19): authorise an administrator, load the
 * subscription (which carries the price it was sold at) and its payments, ask the {@link PaymentLedger} for the new payment, then store
 * the subscription <em>first</em> and the payment second. Saving the subscription takes its row lock and checks its
 * version, so two payments or reversals for one subscription queue up and the loser retries against the payments the
 * winner left (never both passing the "amount due" or "already reversed" checks); a subscription is saved even
 * when its status does not change, precisely for that.
 */
final class PaymentSettling {

    /** What was stored: the new payment, the subscription as saved and the ledger including the new payment. */
    record Settled(Payment payment, Subscription subscription, PaymentLedger ledger) {
    }

    private final MemberRepository members;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;

    PaymentSettling(MemberRepository members, SubscriptionRepository subscriptions, PaymentRepository payments) {
        this.members = members;
        this.subscriptions = subscriptions;
        this.payments = payments;
    }

    /** Authorises the actor as an administrator of the association. */
    Member requireAdmin(Actor actor, String action) {
        Member admin = Lookups.member(members, actor.associationId(), actor.memberId());
        Permissions.requireAdmin(admin, action);
        return admin;
    }

    /**
     * @param newPayment decides the new payment (a payment or a reversal) from the subscription's ledger as it is now
     */
    Settled settle(AssociationId associationId, SubscriptionId subscriptionId, Function<PaymentLedger, Payment> newPayment) {
        Subscription subscription = Lookups.subscription(subscriptions, associationId, subscriptionId);
        List<Payment> existing = payments.findBySubscription(associationId, subscriptionId);

        PaymentLedger ledger = PaymentLedger.of(subscription, existing);
        Payment payment = newPayment.apply(ledger);
        PaymentLedger after = ledger.with(payment);
        Subscription stored = subscriptions.save(after.settle(subscription));
        Payment storedPayment = payments.add(payment);
        return new Settled(storedPayment, stored, after);
    }
}
