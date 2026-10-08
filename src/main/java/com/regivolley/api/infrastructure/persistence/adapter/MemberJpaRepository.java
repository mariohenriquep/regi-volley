package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    List<MemberJpaEntity> findByAssociationIdAndIdIn(UUID associationId, Collection<UUID> ids);

    Optional<MemberJpaEntity> findByAssociationIdAndEmail(UUID associationId, String email);

    /** Ids of the active administrators, in id order: a scalar query, so it is always answered by the database. */
    @Query("""
            select m.id from MemberJpaEntity m
            where m.associationId = :associationId and m.status = 'ACTIVE'
              and exists (select 1 from m.roles r where r.role = 'ADMIN')
            order by m.id
            """)
    List<UUID> findActiveAdminIds(@Param("associationId") UUID associationId);
}
