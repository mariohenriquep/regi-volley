package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code refresh_token}. State changes are single conditional statements, so parallel requests cannot both win. */
interface RefreshTokenJpaRepository extends Repository<RefreshTokenJpaEntity, UUID> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.usedAt = :at where t.id = :id and t.usedAt is null and t.revokedAt is null")
    int markUsed(@Param("id") UUID id, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :at where t.familyId = :family and t.revokedAt is null")
    int revokeFamily(@Param("family") UUID family, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :at where t.userId = :user and t.revokedAt is null")
    int revokeAllOf(@Param("user") UUID user, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshTokenJpaEntity t where t.userId = :user and (t.expiresAt <= :now or t.idleExpiresAt <= :now)")
    int deleteExpiredOf(@Param("user") UUID user, @Param("now") Instant now);
}
