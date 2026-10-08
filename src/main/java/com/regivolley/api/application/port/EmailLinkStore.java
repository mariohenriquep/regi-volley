package com.regivolley.api.application.port;

import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Port to the {@code email_link} table, implemented in {@code infrastructure.persistence.adapter}. Looked up by hash, never by value. */
public interface EmailLinkStore {

    EmailLink insert(EmailLink link);

    Optional<EmailLink> findByTokenHash(String tokenHash);

    /**
     * Consumes the link, only if it is still unconsumed. Returns whether this call did it: of two parallel uses of one link
     * exactly one gets {@code true}.
     */
    boolean consume(UUID linkId, Instant at);

    /** Supersedes the membership's open links of this purpose (a newer link invalidates older ones). Returns how many. */
    int invalidateOpenForMembership(UUID membershipId, AccountLinkPurpose purpose, Instant at);

    /** Supersedes the user's open links of this purpose. Returns how many. */
    int invalidateOpenForUser(UUID userId, AccountLinkPurpose purpose, Instant at);

    /** Deletes the user's links that are consumed or expired (housekeeping on write). Returns how many. */
    int deleteSpentOf(UUID userId, Instant now);
}
