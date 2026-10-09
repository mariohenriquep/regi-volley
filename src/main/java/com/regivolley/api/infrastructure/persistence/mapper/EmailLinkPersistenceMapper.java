package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.infrastructure.persistence.entity.EmailLinkJpaEntity;
import com.regivolley.api.application.identity.EmailLink;

/** Translates an {@link EmailLink} to and from {@link EmailLinkJpaEntity}. */
public final class EmailLinkPersistenceMapper {

    private EmailLinkPersistenceMapper() {
    }

    public static EmailLink toDomain(EmailLinkJpaEntity entity) {
        return new EmailLink(entity.getId(), entity.getUserId(), entity.getMembershipId(),
                AccountLinkPurpose.valueOf(entity.getPurpose()), entity.getTokenHash(), entity.getCreatedAt(),
                entity.getExpiresAt(), entity.getConsumedAt());
    }

    public static EmailLinkJpaEntity toNewEntity(EmailLink link) {
        EmailLinkJpaEntity entity = new EmailLinkJpaEntity();
        entity.setId(link.id());
        entity.setUserId(link.userId());
        entity.setMembershipId(link.membershipId());
        entity.setPurpose(link.purpose().name());
        entity.setTokenHash(link.tokenHash());
        entity.setCreatedAt(link.createdAt());
        entity.setExpiresAt(link.expiresAt());
        entity.setConsumedAt(link.consumedAt());
        return entity;
    }
}
