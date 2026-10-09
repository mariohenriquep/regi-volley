package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.MembershipJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code membership}. Every read that starts from a member carries the association. */
interface MembershipJpaRepository extends Repository<MembershipJpaEntity, UUID> {

    Optional<MembershipJpaEntity> findById(UUID id);

    List<MembershipJpaEntity> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    Optional<MembershipJpaEntity> findByUserIdAndAssociationId(UUID userId, UUID associationId);

    Optional<MembershipJpaEntity> findByAssociationIdAndMemberId(UUID associationId, UUID memberId);

    Optional<MembershipJpaEntity> findByUserIdAndAssociationIdAndMemberId(UUID userId, UUID associationId, UUID memberId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MembershipJpaEntity m set m.status = 'CONFIRMED', m.confirmedAt = :at where m.id = :id and m.status = 'PENDING'")
    int confirm(@Param("id") UUID id, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MembershipJpaEntity m where m.id = :id")
    int deleteMembership(@Param("id") UUID id);
}
