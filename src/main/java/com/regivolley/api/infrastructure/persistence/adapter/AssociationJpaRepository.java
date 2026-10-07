package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.AssociationJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to associations (the tenants); the short name is the only public lookup. */
interface AssociationJpaRepository extends JpaRepository<AssociationJpaEntity, UUID> {

    /** The association row locked for update without loading its levels: a save locks the root first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssociationJpaEntity> findForUpdateById(UUID id);

    Optional<AssociationJpaEntity> findByShortName(String shortName);

    boolean existsByShortName(String shortName);

    @Query("select a.id from AssociationJpaEntity a order by a.id")
    List<UUID> findAllIds();
}
