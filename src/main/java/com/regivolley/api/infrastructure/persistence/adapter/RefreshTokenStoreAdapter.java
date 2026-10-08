package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.infrastructure.persistence.mapper.RefreshTokenPersistenceMapper;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.port.RefreshTokenStore;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@link RefreshTokenStore} on Spring Data JPA. Only hashes pass through here. */
@Component
public class RefreshTokenStoreAdapter implements RefreshTokenStore {

    private final RefreshTokenJpaRepository tokens;
    private final EntityManager entityManager;

    public RefreshTokenStoreAdapter(RefreshTokenJpaRepository tokens, EntityManager entityManager) {
        this.tokens = tokens;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public RefreshToken insert(RefreshToken token) {
        try {
            entityManager.persist(RefreshTokenPersistenceMapper.toNewEntity(token));
            entityManager.flush();
        } catch (ConstraintViolationException e) {
            // A membership that is not the user's own, or a hash that already exists.
            throw new DataIntegrityViolationException("A refresh token violates a database constraint", e);
        }
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return tokens.findByTokenHash(tokenHash).map(RefreshTokenPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean markUsed(UUID tokenId, Instant at) {
        return tokens.markUsed(tokenId, at) == 1;
    }

    @Override
    @Transactional
    public int revokeFamily(UUID familyId, Instant at) {
        return tokens.revokeFamily(familyId, at);
    }

    @Override
    @Transactional
    public int revokeAllOf(UUID userId, Instant at) {
        return tokens.revokeAllOf(userId, at);
    }

    @Override
    @Transactional
    public int deleteExpiredOf(UUID userId, Instant now) {
        return tokens.deleteExpiredOf(userId, now);
    }
}
