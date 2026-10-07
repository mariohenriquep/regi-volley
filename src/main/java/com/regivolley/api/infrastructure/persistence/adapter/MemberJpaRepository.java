package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data access to members. Every query carries the association. */
interface MemberJpaRepository extends JpaRepository<MemberJpaEntity, UUID> {

    Optional<MemberJpaEntity> findByIdAndAssociationId(UUID id, UUID associationId);

    /**
     * Same row as {@code findByIdAndAssociationId}, but locked for update without loading any child rows:
     * a save locks the root first, so concurrent writers queue up on it instead of deadlocking on their children.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MemberJpaEntity> findForUpdateByIdAndAssociationId(UUID id, UUID associationId);

    Optional<MemberJpaEntity> findByAssociationIdAndEmail(UUID associationId, String email);
}
