package com.regivolley.api.infrastructure.devseed;

import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Does in one step what the emailed activation link does in two (set the first password, confirm the membership), for the seeded
 * members only: there is no mailbox to read in a fresh dev database. It works on the account and the PENDING membership that
 * {@code RegisterAssociation} / {@code ApproveJoinRequest} already provisioned, through the credential ports, and closes the
 * activation link that provisioning mailed, so the link left in Mailpit can no longer be used. It is a bean of profile {@code dev}
 * only ({@link DevSeedOnly}); nothing in the production activation flow changes and no endpoint reaches it.
 */
@DevSeedOnly
class DevAccountActivator {

    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final EmailLinkStore links;
    private final PasswordHasher hasher;
    private final SecretGenerator secrets;
    private final TransactionRunner transactions;
    private final Clock clock;

    DevAccountActivator(UserAccountStore users, MembershipStore memberships, EmailLinkStore links, PasswordHasher hasher,
                        SecretGenerator secrets, TransactionRunner transactions, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.links = links;
        this.hasher = hasher;
        this.secrets = secrets;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Gives the member's account the password and confirms the membership; returns the account's id. */
    UUID activate(AssociationId associationId, MemberId memberId, String email, String password) {
        String hash = hasher.hash(password);
        return transactions.inNewTransaction(() -> {
            Instant now = clock.instant();
            UserAccount user = users.findByEmail(EmailAddress.of(email))
                    .orElseThrow(() -> new IllegalStateException("No account was provisioned for member " + memberId));
            Membership membership = memberships.findByMember(associationId, memberId)
                    .filter(found -> found.userId().equals(user.id()))
                    .orElseThrow(() -> new IllegalStateException("No membership was provisioned for member " + memberId));
            links.invalidateOpenForMembership(membership.id(), AccountLinkPurpose.ACTIVATION, now);
            users.setPassword(user.id(), hash, secrets.newSecret(), now);
            memberships.confirm(membership.id(), now);
            return user.id();
        });
    }
}
