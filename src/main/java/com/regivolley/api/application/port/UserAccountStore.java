package com.regivolley.api.application.port;

import com.regivolley.api.application.exception.AccountAlreadyExistsException;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.domain.model.valueobject.EmailAddress;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port to the {@code app_user} table, implemented in {@code infrastructure.persistence.adapter}. Credentials are changed
 * only by the targeted operations below, each one a single statement: a copy of an account loaded a moment ago can never
 * write an old password hash or an old security stamp back over a reset (which would revive revoked sessions).
 */
public interface UserAccountStore {

    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByEmail(EmailAddress email);

    /** @throws AccountAlreadyExistsException if the email is taken (a race the caller's {@link #findByEmail} cannot rule out) */
    UserAccount insert(UserAccount account);

    /** Sets the password hash and the new security stamp together: whoever holds an access token with the old stamp is out. */
    void setPassword(UUID userId, String passwordHash, String newSecurityStamp, Instant at);

    /** Replaces the security stamp only (logout-all). */
    void rotateSecurityStamp(UUID userId, String newSecurityStamp, Instant at);

    /**
     * Swaps the hash for a stronger one of the same password (transparent upgrade at login), only if the stored hash is still
     * {@code expectedHash}. Returns whether it did; false means the password changed meanwhile and must not be overwritten.
     */
    boolean replacePasswordHash(UUID userId, String expectedHash, String newHash, Instant at);

    /** Deletes the account with its memberships, refresh tokens and links (erasure). */
    void deleteById(UUID userId);
}
