package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.TrainingGroupJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to training groups. Every query carries the association. */
interface TrainingGroupJpaRepository extends JpaRepository<TrainingGroupJpaEntity, UUID> {

    Optional<TrainingGroupJpaEntity> findByIdAndAssociationId(UUID id, UUID associationId);

    /**
     * Same row as {@code findByIdAndAssociationId}, but locked for update without loading any child rows:
     * a save locks the root first, so concurrent writers queue up on it instead of deadlocking on their children.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TrainingGroupJpaEntity> findForUpdateByIdAndAssociationId(UUID id, UUID associationId);

    boolean existsByAssociationIdAndVenueIdAndStatus(UUID associationId, UUID venueId, String status);

    List<TrainingGroupJpaEntity> findAllByAssociationIdOrderByNameAscIdAsc(UUID associationId);
}
