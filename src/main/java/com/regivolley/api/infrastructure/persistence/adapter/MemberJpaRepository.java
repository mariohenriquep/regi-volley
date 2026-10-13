package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
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

    /**
     * The association's members who have not been anonymised, by lower-cased name then id, narrowed by status and by a lower-cased
     * {@code like} pattern over the name or the email (backslash is the escape character); a null status or pattern does not narrow.
     * The page bounds the result.
     */
    @Query("""
            select m from MemberJpaEntity m
            where m.associationId = :associationId and m.anonymisedAt is null
              and (:status is null or m.status = :status)
              and (:pattern is null or lower(m.name) like :pattern escape '\\' or lower(m.email) like :pattern escape '\\')
            order by lower(m.name), m.id
            """)
    List<MemberJpaEntity> search(@Param("associationId") UUID associationId, @Param("status") String status,
                                 @Param("pattern") String pattern, Pageable pageable);

    /** Ids of the active administrators, in id order: a scalar query, so it is always answered by the database. */
    @Query("""
            select m.id from MemberJpaEntity m
            where m.associationId = :associationId and m.status = 'ACTIVE'
              and exists (select 1 from m.roles r where r.role = 'ADMIN')
            order by m.id
            """)
    List<UUID> findActiveAdminIds(@Param("associationId") UUID associationId);
}
