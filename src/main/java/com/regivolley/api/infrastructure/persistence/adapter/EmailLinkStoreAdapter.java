package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.application.exception.LinkAlreadyIssuedException;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.infrastructure.persistence.mapper.EmailLinkPersistenceMapper;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.port.EmailLinkStore;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** {@link EmailLinkStore} on Spring Data JPA. Only hashes pass through here. */
@Component
public class EmailLinkStoreAdapter implements EmailLinkStore {

    private static final String OPEN_ACTIVATION_INDEX = "uq_email_link_open_activation";
    private static final String OPEN_RESET_INDEX = "uq_email_link_open_reset";

    private final EmailLinkJpaRepository links;
    private final EntityManager entityManager;

    public EmailLinkStoreAdapter(EmailLinkJpaRepository links, EntityManager entityManager) {
        this.links = links;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public EmailLink insert(EmailLink link) {
        try {
            entityManager.persist(EmailLinkPersistenceMapper.toNewEntity(link));
            entityManager.flush();
        } catch (ConstraintViolationException e) {
            if (WriteSupport.violates(e, OPEN_ACTIVATION_INDEX) || WriteSupport.violates(e, OPEN_RESET_INDEX)) {
                throw new LinkAlreadyIssuedException();
            }
            throw new DataIntegrityViolationException("A link violates a database constraint", e);
        }
        return link;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailLink> findByTokenHash(String tokenHash) {
        return links.findByTokenHash(tokenHash).map(EmailLinkPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean consume(UUID linkId, Instant at) {
        return links.consume(linkId, at) == 1;
    }

    @Override
    @Transactional
    public int invalidateOpenForMembership(UUID membershipId, AccountLinkPurpose purpose, Instant at) {
        return links.invalidateOpenForMembership(membershipId, purpose.name(), at);
    }

    @Override
    @Transactional
    public int invalidateOpenForUser(UUID userId, AccountLinkPurpose purpose, Instant at) {
        return links.invalidateOpenForUser(userId, purpose.name(), at);
    }

    @Override
    @Transactional
    public int deleteSpentOf(UUID userId, Instant now) {
        return links.deleteSpentOf(userId, now);
    }
}
