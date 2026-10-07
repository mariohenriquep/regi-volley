package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.SubscriptionJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to subscriptions. Every query carries the association. */
interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    Optional<SubscriptionJpaEntity> findByIdAndAssociationId(UUID id, UUID associationId);

    /**
     * Same row as {@code findByIdAndAssociationId}, but locked for update without loading any child rows:
     * a save locks the root first, so concurrent writers queue up on it instead of deadlocking on their children.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SubscriptionJpaEntity> findForUpdateByIdAndAssociationId(UUID id, UUID associationId);

    List<SubscriptionJpaEntity> findByAssociationIdAndMemberIdOrderByStartDateAscIdAsc(UUID associationId, UUID memberId);
}
