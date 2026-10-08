package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.VenueJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to venues. Every query carries the association. */
interface VenueJpaRepository extends JpaRepository<VenueJpaEntity, UUID> {

    Optional<VenueJpaEntity> findByIdAndAssociationId(UUID id, UUID associationId);

    /** Same row as {@code findByIdAndAssociationId}, but locked for update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VenueJpaEntity> findForUpdateByIdAndAssociationId(UUID id, UUID associationId);

    List<VenueJpaEntity> findAllByAssociationIdOrderByNameAscIdAsc(UUID associationId);
}
