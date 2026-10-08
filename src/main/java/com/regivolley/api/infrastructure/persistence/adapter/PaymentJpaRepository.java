package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.PaymentJpaEntity;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data access to payments, read-only on purpose: it extends the bare {@link Repository} marker, so it has
 * no save, update or delete (RN-19). Rows are inserted through the entity manager by the adapter. Every query carries the association.
 */
interface PaymentJpaRepository extends Repository<PaymentJpaEntity, UUID> {

    Optional<PaymentJpaEntity> findByIdAndAssociationId(UUID id, UUID associationId);

    List<PaymentJpaEntity> findByAssociationIdAndSubscriptionIdOrderByRecordedAtAscIdAsc(UUID associationId, UUID subscriptionId);
}
