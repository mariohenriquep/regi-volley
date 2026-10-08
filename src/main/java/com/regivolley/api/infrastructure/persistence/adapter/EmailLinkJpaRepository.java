package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.EmailLinkJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code email_link}. State changes are single conditional statements, so parallel uses cannot both win. */
interface EmailLinkJpaRepository extends Repository<EmailLinkJpaEntity, UUID> {

    Optional<EmailLinkJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailLinkJpaEntity l set l.consumedAt = :at where l.id = :id and l.consumedAt is null")
    int consume(@Param("id") UUID id, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailLinkJpaEntity l set l.consumedAt = :at "
            + "where l.membershipId = :membership and l.purpose = :purpose and l.consumedAt is null")
    int invalidateOpenForMembership(@Param("membership") UUID membership, @Param("purpose") String purpose, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailLinkJpaEntity l set l.consumedAt = :at where l.userId = :user and l.purpose = :purpose and l.consumedAt is null")
    int invalidateOpenForUser(@Param("user") UUID user, @Param("purpose") String purpose, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailLinkJpaEntity l where l.userId = :user and (l.consumedAt is not null or l.expiresAt <= :now)")
    int deleteSpentOf(@Param("user") UUID user, @Param("now") Instant now);
}
