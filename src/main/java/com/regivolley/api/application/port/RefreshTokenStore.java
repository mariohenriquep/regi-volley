package com.regivolley.api.application.port;

import com.regivolley.api.application.identity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port to the {@code refresh_token} table, implemented in {@code infrastructure.persistence.adapter}. Looked up by hash, never by value. */
public interface RefreshTokenStore {

    RefreshToken insert(RefreshToken token);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marks the token rotated, only if it has not been used or revoked. Returns whether this call did it: of two parallel
     * presentations of one token exactly one gets {@code true} and rotates; the other must treat the token as just rotated.
     */
    boolean markUsed(UUID tokenId, Instant at);

    /** Revokes every live token of the family. Returns how many. */
    int revokeFamily(UUID familyId, Instant at);

    /** Revokes every live token of the user, in every family. Returns how many. */
    int revokeAllOf(UUID userId, Instant at);

    /** Deletes the tokens of the user that are past either lifetime (housekeeping on write). Returns how many. */
    int deleteExpiredOf(UUID userId, Instant now);
}
