package com.regivolley.api.application.port;

import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port to the {@code membership} table, implemented in {@code infrastructure.persistence.adapter}. A membership belongs to
 * an association, so the reads that start from a member take the {@link AssociationId} and another tenant's member is
 * simply absent (architecture.md section 8).
 */
public interface MembershipStore {

    Optional<Membership> findById(UUID id);

    List<Membership> findByUser(UUID userId);

    Optional<Membership> findByUserAndAssociation(UUID userId, AssociationId associationId);

    Optional<Membership> findByMember(AssociationId associationId, MemberId memberId);

    Membership insert(Membership membership);

    /** PENDING to CONFIRMED. Returns whether it changed (false when it was already confirmed or does not exist). */
    boolean confirm(UUID membershipId, Instant at);

    void deleteById(UUID membershipId);
}
