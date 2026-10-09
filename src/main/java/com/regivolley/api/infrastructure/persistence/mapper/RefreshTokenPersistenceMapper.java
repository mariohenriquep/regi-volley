package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.infrastructure.persistence.entity.RefreshTokenJpaEntity;
import com.regivolley.api.application.identity.RefreshToken;

/** Translates a {@link RefreshToken} to and from {@link RefreshTokenJpaEntity}. */
public final class RefreshTokenPersistenceMapper {

    private RefreshTokenPersistenceMapper() {
    }

    public static RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return new RefreshToken(entity.getId(), entity.getFamilyId(), entity.getUserId(), entity.getMembershipId(),
                entity.getTokenHash(), entity.getParentId(), entity.getIssuedAt(), entity.getExpiresAt(),
                entity.getIdleExpiresAt(), entity.getUsedAt(), entity.getRevokedAt());
    }

    public static RefreshTokenJpaEntity toNewEntity(RefreshToken token) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setId(token.id());
        entity.setFamilyId(token.familyId());
        entity.setUserId(token.userId());
        entity.setMembershipId(token.membershipId());
        entity.setTokenHash(token.tokenHash());
        entity.setParentId(token.parentId());
        entity.setIssuedAt(token.issuedAt());
        entity.setExpiresAt(token.expiresAt());
        entity.setIdleExpiresAt(token.idleExpiresAt());
        entity.setUsedAt(token.usedAt());
        entity.setRevokedAt(token.revokedAt());
        return entity;
    }
}
