package com.regivolley.api.application.identity;

import com.regivolley.api.application.identity.AccountLinkPurpose;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single-use emailed link as stored (table {@code email_link}); only the hash of its token exists here (threat model D-11).
 * An ACTIVATION link names the membership it confirms; a PASSWORD_RESET link belongs to the account alone.
 */
public record EmailLink(UUID id, UUID userId, UUID membershipId, AccountLinkPurpose purpose, String tokenHash,
                        Instant createdAt, Instant expiresAt, Instant consumedAt) {

    public EmailLink {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if ((purpose == AccountLinkPurpose.ACTIVATION) != (membershipId != null)) {
            throw new IllegalArgumentException("an activation link names its membership and a reset link does not");
        }
    }

    /** Usable now: not consumed and not past its expiry. */
    public boolean isUsableAt(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "EmailLink{id=" + id + ", purpose=" + purpose + "}";
    }
}
