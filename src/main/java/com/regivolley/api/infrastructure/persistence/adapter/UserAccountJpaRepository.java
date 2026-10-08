package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.entity.UserAccountJpaEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data access to {@code app_user}. Credentials change only through the single-statement updates below, so no stale
 * copy of an account can write an old hash or stamp back.
 */
interface UserAccountJpaRepository extends Repository<UserAccountJpaEntity, UUID> {

    Optional<UserAccountJpaEntity> findById(UUID id);

    Optional<UserAccountJpaEntity> findByEmail(String email);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAccountJpaEntity u set u.passwordHash = :hash, u.securityStamp = :stamp, u.updatedAt = :at where u.id = :id")
    int setPassword(@Param("id") UUID id, @Param("hash") String hash, @Param("stamp") String stamp, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAccountJpaEntity u set u.securityStamp = :stamp, u.updatedAt = :at where u.id = :id")
    int rotateStamp(@Param("id") UUID id, @Param("stamp") String stamp, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update UserAccountJpaEntity u set u.passwordHash = :newHash, u.updatedAt = :at where u.id = :id and u.passwordHash = :expected")
    int replaceHash(@Param("id") UUID id, @Param("expected") String expected, @Param("newHash") String newHash, @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserAccountJpaEntity u where u.id = :id")
    int deleteUser(@Param("id") UUID id);
}
