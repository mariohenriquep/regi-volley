package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.infrastructure.persistence.mapper.UserAccountPersistenceMapper;
import com.regivolley.api.application.exception.AccountAlreadyExistsException;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.UserAccountStore;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@link UserAccountStore} on Spring Data JPA. Nothing here logs an email address or a hash. */
@Component
public class UserAccountStoreAdapter implements UserAccountStore {

    private static final String EMAIL_CONSTRAINT = "uq_app_user_email";

    private final UserAccountJpaRepository accounts;
    private final EntityManager entityManager;

    public UserAccountStoreAdapter(UserAccountJpaRepository accounts, EntityManager entityManager) {
        this.accounts = accounts;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(UUID id) {
        return accounts.findById(id).map(UserAccountPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(EmailAddress email) {
        return accounts.findByEmail(email.value()).map(UserAccountPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public UserAccount insert(UserAccount account) {
        try {
            entityManager.persist(UserAccountPersistenceMapper.toNewEntity(account));
            entityManager.flush();
        } catch (ConstraintViolationException e) {
            if (WriteSupport.violates(e, EMAIL_CONSTRAINT)) {
                // The cause is dropped on purpose: it could quote the email address.
                throw new AccountAlreadyExistsException();
            }
            throw e;
        }
        return account;
    }

    @Override
    @Transactional
    public void setPassword(UUID userId, String passwordHash, String newSecurityStamp, Instant at) {
        accounts.setPassword(userId, passwordHash, newSecurityStamp, at);
    }

    @Override
    @Transactional
    public void rotateSecurityStamp(UUID userId, String newSecurityStamp, Instant at) {
        accounts.rotateStamp(userId, newSecurityStamp, at);
    }

    @Override
    @Transactional
    public boolean replacePasswordHash(UUID userId, String expectedHash, String newHash, Instant at) {
        return accounts.replaceHash(userId, expectedHash, newHash, at) == 1;
    }

    @Override
    @Transactional
    public void deleteById(UUID userId) {
        accounts.deleteUser(userId);
    }
}
