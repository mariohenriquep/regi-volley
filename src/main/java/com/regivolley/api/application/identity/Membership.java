package com.regivolley.api.application.identity;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The link between an account and a member of one association (table {@code membership}). PENDING until the emailed
 * activation link is consumed, CONFIRMED afterwards; only a confirmed membership can sign in (threat model D-11). The tenant
 * columns are the only ones that bind an account to an association, and the database keeps {@code memberId} inside it.
 */
public record Membership(UUID id, UUID userId, AssociationId associationId, MemberId memberId, MembershipStatus status,
                         Instant createdAt, Instant confirmedAt) {

    public Membership {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if ((status == MembershipStatus.CONFIRMED) != (confirmedAt != null)) {
            throw new IllegalArgumentException("a membership has a confirmation time exactly when it is confirmed");
        }
    }

    /** A new membership that cannot be used until its activation link is consumed. */
    public static Membership pending(UUID userId, AssociationId associationId, MemberId memberId, Instant now) {
        return new Membership(UUID.randomUUID(), userId, associationId, memberId, MembershipStatus.PENDING, now, null);
    }

    public boolean isConfirmed() {
        return status == MembershipStatus.CONFIRMED;
    }
}
