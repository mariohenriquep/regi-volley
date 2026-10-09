package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.application.port.AccountProvisioner;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Gives a new member a way to sign in (threat model D-11, section 6), for {@code RegisterAssociation} (the founder) and
 * {@code ApproveJoinRequest}: an account for the email (or the existing one, untouched), a PENDING membership for the member,
 * and a single-use ACTIVATION link, mailed to the <em>account's</em> address once the transaction has committed. Nobody gets a
 * password without owning the mailbox (L3), and the membership stays unusable until the link is consumed (L4).
 *
 * <p>Idempotent: asking again for the same member creates nothing twice and issues a fresh link, superseding the old one. Two
 * provisionings racing to create the same account, or the same link, are resolved by the database and repeated. A member already
 * confirmed, or an account that is disabled, gets nothing; a member that belongs to another account is refused.
 */
@Service
public class AccountProvisionerService implements AccountProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(AccountProvisionerService.class);

    private record Issued(UserAccount account, AccountLink link) {
    }

    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final EmailLinkIssuer links;
    private final AccountLinkMailer mailer;
    private final TransactionRunner transactions;
    private final SecretGenerator secrets;
    private final Clock clock;

    public AccountProvisionerService(UserAccountStore users, MembershipStore memberships, EmailLinkStore links, SecretGenerator secrets,
                                     AccountLinkMailer mailer, TransactionRunner transactions, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.links = new EmailLinkIssuer(links, secrets, clock);
        this.mailer = mailer;
        this.transactions = transactions;
        this.secrets = secrets;
        this.clock = clock;
    }

    @Override
    public void provision(AssociationId associationId, MemberId memberId, EmailAddress email) {
        Issued issued = Conflicts.retrying(transactions, () -> inTransaction(associationId, memberId, email));
        if (issued == null) {
            LOG.info("Nothing to send, the membership is already active or the account is disabled: association={} member={}", associationId, memberId);
            return;
        }
        mailer.send(issued.account().email(), AccountLinkPurpose.ACTIVATION, issued.link());
        LOG.info("Credentials provisioned: association={} member={} link={}", associationId, memberId, issued.link().reference());
    }

    private Issued inTransaction(AssociationId associationId, MemberId memberId, EmailAddress email) {
        Instant now = clock.instant();
        UserAccount existing = users.findByEmail(email).orElse(null);
        Membership membership = memberships.findByMember(associationId, memberId).orElse(null);
        if (membership != null && (existing == null || !membership.userId().equals(existing.id()))) {
            // Checked before anything is created: this member is already tied to a different account.
            throw new IllegalStateException("The member already belongs to another account");
        }
        if (existing != null && existing.status() != UserStatus.ACTIVE) {
            // A disabled account is never given a membership or mailed a link: nothing would be allowed to sign in with it anyway.
            return null;
        }
        UserAccount user = existing != null ? existing : users.insert(UserAccount.create(email, secrets.newSecret(), now));
        if (membership == null) {
            membership = memberships.insert(Membership.pending(user.id(), associationId, memberId, now));
        }
        if (membership.isConfirmed()) {
            return null;
        }
        return new Issued(user, links.issue(user, membership, AccountLinkPurpose.ACTIVATION));
    }
}
