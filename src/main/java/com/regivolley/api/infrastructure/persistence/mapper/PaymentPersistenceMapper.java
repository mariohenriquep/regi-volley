package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.infrastructure.persistence.entity.PaymentJpaEntity;

/** Translates a {@link Payment} to and from {@link PaymentJpaEntity}. Payments are only ever inserted, never copied onto a stored row. */
public final class PaymentPersistenceMapper {

    private PaymentPersistenceMapper() {
    }

    /** Rebuilds the aggregate, re-checking its invariants. */
    public static Payment toDomain(PaymentJpaEntity entity) {
        return Payment.reconstruct(new PaymentId(entity.getId()), new AssociationId(entity.getAssociationId()),
                new SubscriptionId(entity.getSubscriptionId()), Money.ofCents(entity.getAmountCents()),
                entity.getPaidOn(), PaymentMethod.valueOf(entity.getMethod()), new MemberId(entity.getRecordedBy()),
                entity.getRecordedAt(), entity.getReversalOf() == null ? null : new PaymentId(entity.getReversalOf()));
    }

    /** A new entity holding the payment, ready to insert. */
    public static PaymentJpaEntity toNewEntity(Payment payment) {
        PaymentJpaEntity entity = new PaymentJpaEntity();
        entity.setId(payment.id().value());
        entity.setAssociationId(payment.associationId().value());
        entity.setSubscriptionId(payment.subscriptionId().value());
        entity.setAmountCents(payment.amount().cents());
        entity.setPaidOn(payment.paidOn());
        entity.setMethod(payment.method().name());
        entity.setRecordedBy(payment.recordedBy().value());
        entity.setRecordedAt(payment.recordedAt());
        entity.setReversalOf(payment.reversalOf().map(PaymentId::value).orElse(null));
        return entity;
    }
}
