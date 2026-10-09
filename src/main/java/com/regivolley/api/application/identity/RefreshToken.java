package com.regivolley.api.application.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A refresh token as stored (table {@code refresh_token}); only its hash exists here, never the value (threat model D-7).
 * A family is one login; each rotation issues a successor in the same family and marks the old token used. {@code expiresAt}
 * is the family's absolute end (90 days after the login) and {@code idleExpiresAt} moves 30 days past each rotation.
 */
public record RefreshToken(UUID id, UUID familyId, UUID userId, UUID membershipId, String tokenHash, UUID parentId,
                           Instant issuedAt, Instant expiresAt, Instant idleExpiresAt, Instant usedAt, Instant revokedAt) {

    public RefreshToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(familyId, "familyId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(idleExpiresAt, "idleExpiresAt must not be null");
    }

    /** The first token of a new family (a login). */
    public static RefreshToken first(UUID userId, UUID membershipId, String tokenHash, Instant now) {
        UUID id = UUID.randomUUID();
        return new RefreshToken(id, UUID.randomUUID(), userId, membershipId, tokenHash, null, now,
                now.plus(RefreshTokenPolicy.ABSOLUTE_LIFETIME), now.plus(RefreshTokenPolicy.IDLE_LIFETIME), null, null);
    }

    /** The successor of this token in the same family: the absolute end is inherited, the idle end restarts. */
    public RefreshToken successor(String tokenHash, Instant now) {
        return new RefreshToken(UUID.randomUUID(), familyId, userId, membershipId, tokenHash, id, now, expiresAt,
                now.plus(RefreshTokenPolicy.IDLE_LIFETIME).isBefore(expiresAt) ? now.plus(RefreshTokenPolicy.IDLE_LIFETIME) : expiresAt,
                null, null);
    }

    /** Whether it is past either lifetime. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(idleExpiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    @Override
    public String toString() {
        return "RefreshToken{id=" + id + ", family=" + familyId + "}";
    }
}
