package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.PlanJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to plans. Every query carries the association. */
interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, UUID> {

    Optional<PlanJpaEntity> findByIdAndAssociationId(UUID id, UUID associationId);

    List<PlanJpaEntity> findByAssociationIdAndIdIn(UUID associationId, Collection<UUID> ids);

    @Query("select p from PlanJpaEntity p where p.associationId = :associationId order by lower(p.name), p.id")
    List<PlanJpaEntity> findAllByAssociation(@Param("associationId") UUID associationId);

    /**
     * Same row as {@code findByIdAndAssociationId}, but locked for update without loading any child rows:
     * a save locks the root first, so concurrent writers queue up on it instead of deadlocking on their children.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlanJpaEntity> findForUpdateByIdAndAssociationId(UUID id, UUID associationId);
}
