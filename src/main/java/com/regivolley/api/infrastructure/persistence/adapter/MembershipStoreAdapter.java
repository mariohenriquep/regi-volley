package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.infrastructure.persistence.mapper.MembershipPersistenceMapper;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.port.MembershipStore;
import jakarta.persistence.EntityManager;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link MembershipStore} on Spring Data JPA. A membership belongs to an association; the database's composite foreign key
 * keeps its member inside it, and the reads that start from a member take the association (architecture.md section 8).
 */
@Component
public class MembershipStoreAdapter implements MembershipStore {

    private final MembershipJpaRepository memberships;
    private final EntityManager entityManager;

    public MembershipStoreAdapter(MembershipJpaRepository memberships, EntityManager entityManager) {
        this.memberships = memberships;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> findById(UUID id) {
        return memberships.findById(id).map(MembershipPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Membership> findByUser(UUID userId) {
        return memberships.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream().map(MembershipPersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> findByUserAndAssociation(UUID userId, AssociationId associationId) {
        return memberships.findByUserIdAndAssociationId(userId, associationId.value()).map(MembershipPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Membership> findByMember(AssociationId associationId, MemberId memberId) {
        return memberships.findByAssociationIdAndMemberId(associationId.value(), memberId.value())
                .map(MembershipPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Membership insert(Membership membership) {
        try {
            entityManager.persist(MembershipPersistenceMapper.toNewEntity(membership));
            entityManager.flush();
        } catch (ConstraintViolationException e) {
            // A second membership for the member or for the user in the association, or a member outside the association.
            throw new DataIntegrityViolationException("A membership violates a database constraint", e);
        }
        return membership;
    }

    @Override
    @Transactional
    public boolean confirm(UUID membershipId, Instant at) {
        return memberships.confirm(membershipId, at) == 1;
    }

    @Override
    @Transactional
    public void deleteById(UUID membershipId) {
        memberships.deleteMembership(membershipId);
    }
}
