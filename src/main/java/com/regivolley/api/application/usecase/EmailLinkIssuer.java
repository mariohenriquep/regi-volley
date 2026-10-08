package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.EmailLinkPolicy;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.SecretGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Creates single-use links (threat model D-11): a 256-bit random token, of which only the hash is stored, valid for the purpose's
 * lifetime. A newer link supersedes the older open ones of the same membership (activation) or user (reset), and the user's spent
 * links are cleared on the way. The clear token is returned once, to be sent; it exists nowhere else. Must run inside the caller's
 * transaction. Two links issued at the same instant are decided by the database (one live link per membership and purpose, one live
 * reset per user): the loser gets {@link com.regivolley.api.application.exception.LinkAlreadyIssuedException} and repeats.
 */
final class EmailLinkIssuer {

    private final EmailLinkStore links;
    private final SecretGenerator secrets;
    private final Clock clock;

    EmailLinkIssuer(EmailLinkStore links, SecretGenerator secrets, Clock clock) {
        this.links = links;
        this.secrets = secrets;
        this.clock = clock;
    }

    /** @param membership the membership an activation confirms; {@code null} for a password reset */
    AccountLink issue(UserAccount user, Membership membership, AccountLinkPurpose purpose) {
        Instant now = clock.instant();
        if (purpose == AccountLinkPurpose.ACTIVATION) {
            links.invalidateOpenForMembership(membership.id(), purpose, now);
        } else {
            links.invalidateOpenForUser(user.id(), purpose, now);
        }
        links.deleteSpentOf(user.id(), now);
        String token = secrets.newSecret();
        Instant expiresAt = now.plus(EmailLinkPolicy.lifetimeOf(purpose));
        EmailLink stored = links.insert(new EmailLink(UUID.randomUUID(), user.id(),
                purpose == AccountLinkPurpose.ACTIVATION ? membership.id() : null, purpose, secrets.hash(token), now, expiresAt, null));
        return new AccountLink(stored.id(), token, expiresAt);
    }
}
