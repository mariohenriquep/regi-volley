package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.infrastructure.persistence.entity.UserAccountJpaEntity;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;

/** Translates a {@link UserAccount} to and from {@link UserAccountJpaEntity}. */
public final class UserAccountPersistenceMapper {

    private UserAccountPersistenceMapper() {
    }

    public static UserAccount toDomain(UserAccountJpaEntity entity) {
        return new UserAccount(entity.getId(), new EmailAddress(entity.getEmail()), UserStatus.valueOf(entity.getStatus()),
                entity.getPasswordHash(), entity.getSecurityStamp(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static UserAccountJpaEntity toNewEntity(UserAccount account) {
        UserAccountJpaEntity entity = new UserAccountJpaEntity();
        entity.setId(account.id());
        entity.setEmail(account.email().value());
        entity.setStatus(account.status().name());
        entity.setPasswordHash(account.passwordHash());
        entity.setSecurityStamp(account.securityStamp());
        entity.setCreatedAt(account.createdAt());
        entity.setUpdatedAt(account.updatedAt());
        return entity;
    }
}
