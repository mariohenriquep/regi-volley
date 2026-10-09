package com.regivolley.api.application.identity;

import com.regivolley.api.domain.model.valueobject.EmailAddress;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A person's login account (table {@code app_user}): the email they sign in with, whether the account is enabled, the hash of
 * their password (absent until they activate it from the emailed link) and the security stamp that access tokens carry. It
 * belongs to no association; the association-bound facts are the {@link Membership}s. Immutable; credentials are changed
 * through {@link com.regivolley.api.application.port.UserAccountStore}'s targeted operations so a stale copy can never write an old password or stamp back.
 *
 * <p>The email is personal data and the hash and stamp are credentials, so {@link #toString()} prints only the id and status.
 */
public record UserAccount(UUID id, EmailAddress email, UserStatus status, String passwordHash, String securityStamp,
                          Instant createdAt, Instant updatedAt) {

    public UserAccount {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(securityStamp, "securityStamp must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * A new enabled account without a password: it cannot sign in until its activation link is consumed.
     *
     * @param securityStamp 256 random bits from the {@code SecretGenerator}, never derived from anything else
     */
    public static UserAccount create(EmailAddress email, String securityStamp, Instant now) {
        return new UserAccount(UUID.randomUUID(), email, UserStatus.ACTIVE, null, securityStamp, now, now);
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    @Override
    public String toString() {
        return "UserAccount{id=" + id + ", status=" + status + "}";
    }
}
