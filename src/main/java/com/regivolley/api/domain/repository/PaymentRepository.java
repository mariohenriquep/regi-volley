package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.util.List;
import java.util.Optional;

/**
 * Port for {@link Payment} aggregates. Payments are append-only (RN-19), so the port can only read and add:
 * there is deliberately no save, update or delete. Every read is scoped to one association (architecture.md section 8).
 */
public interface PaymentRepository {

    Optional<Payment> findById(AssociationId associationId, PaymentId id);

    /** Every payment and reversal of the subscription, oldest first (recorded_at, then id). */
    List<Payment> findBySubscription(AssociationId associationId, SubscriptionId subscriptionId);

    /**
     * Stores a new payment (or reversal) and returns it. An id that already exists is an error, never an overwrite;
     * the database also refuses a second reversal of the same payment and a reversal across subscriptions. Callers
     * serialise on the subscription (saving it first) so those backstops are not normally reached.
     */
    Payment add(Payment payment);
}
