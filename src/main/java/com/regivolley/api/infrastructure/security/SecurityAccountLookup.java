package com.regivolley.api.infrastructure.security;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Optional;
import java.util.UUID;

/**
 * The seam between the token and the credentials tables. Implemented in {@code infrastructure.persistence.adapter} over
 * {@code app_user} (by id) and {@code membership} (by user, association, member); tests use an in-memory double.
 *
 * <p>Contract: answer with the account only if the user exists AND has a membership row at exactly
 * {@code (associationId, memberId)}; otherwise empty. Status checks (disabled account, pending membership, stamp)
 * are the resolver's job, so an implementation must return the stored values as they are and must not cache them
 * (the point of the per-request lookup is immediacy, D-6). Reads are primary-key reads.
 */
public interface SecurityAccountLookup {

    Optional<SecurityAccount> find(UUID userId, AssociationId associationId, MemberId memberId);
}
