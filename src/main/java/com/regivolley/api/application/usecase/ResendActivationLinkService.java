package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ResendActivationLinkCommand;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.application.port.AccountProvisioner;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.application.port.BackgroundWork;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Threat model section 6 and P7. An administrator asks for a member's activation link to be sent again. The request thread only
 * authorises the administrator, takes the association and per-member throttles (before the member is looked up, so an id of another
 * association spends the same budget as one that exists), checks that the member exists in the administrator's association (another
 * association's id is not found, like any unknown one) and takes the per-address mail throttle; everything that depends on the
 * member's credentials happens in {@link BackgroundWork}'s {@code ADMIN_RESEND} lane, which cannot take the capacity password-reset
 * mail needs, so neither the answer nor its timing says whether a link was sent.
 *
 * <p>What the background work does, re-reading the member first (a member deactivated or erased since the request gets nothing): for a
 * PENDING membership the new ACTIVATION link (superseding the older ones, the same {@link EmailLinkIssuer} as provisioning) is mailed
 * to the <em>account's</em> address, never to the member's contact address, which an administrator can edit; a member with no
 * membership at all - provisioning failed after the member was committed (P7) - is provisioned again through the idempotent
 * {@link AccountProvisioner} with their contact address, as at approval. A member who has already activated or whose account is
 * disabled gets nothing. Failures are logged by ids and exception type only.
 */
@Service
public class ResendActivationLinkService implements ResendActivationLinkUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ResendActivationLinkService.class);

    /** What the transaction decided: nobody to email yet, nothing to send, or a fresh link for the account. */
    private sealed interface Step {
        record Provision(EmailAddress contact) implements Step {
        }

        record Skip() implements Step {
        }

        record Send(UserAccount account, AccountLink link) implements Step {
        }
    }

    private final MemberRepository members;
    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final EmailLinkIssuer links;
    private final AccountLinkMailer mailer;
    private final AccountProvisioner provisioner;
    private final AttemptThrottle throttle;
    private final BackgroundWork background;
    private final TransactionRunner transactions;

    public ResendActivationLinkService(MemberRepository members, UserAccountStore users, MembershipStore memberships, EmailLinkStore links,
                                       SecretGenerator secrets, AccountLinkMailer mailer, AccountProvisioner provisioner,
                                       AttemptThrottle throttle, BackgroundWork background, TransactionRunner transactions, Clock clock) {
        this.members = members;
        this.users = users;
        this.memberships = memberships;
        this.links = new EmailLinkIssuer(links, secrets, clock);
        this.mailer = mailer;
        this.provisioner = provisioner;
        this.throttle = throttle;
        this.background = background;
        this.transactions = transactions;
    }

    /** @throws com.regivolley.api.application.exception.RateLimitExceededException if the association, this member or the member's address was asked for too often */
    @Override
    public Void execute(ResendActivationLinkCommand command) {
        AssociationId associationId = command.actor().associationId();
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdmin(actor, "resend an activation link");
        throttle.checkActivationLinkResend(associationId, command.memberId());
        Member target = Lookups.member(members, associationId, command.memberId());
        throttle.checkLinkMailAddress(target.email());
        if (!target.isActive() || target.isAnonymised()) {
            LOG.info("No activation link to resend, the member is not active: association={} member={}", associationId, target.id());
            return null;
        }
        background.run(BackgroundWork.Lane.ADMIN_RESEND, () -> {
            try {
                process(associationId, target.id());
            } catch (RuntimeException e) {
                LOG.warn("An activation link could not be resent: association={} member={} cause={}",
                        associationId, target.id(), e.getClass().getSimpleName());
            }
        });
        return null;
    }

    private void process(AssociationId associationId, MemberId memberId) {
        switch (Conflicts.retrying(transactions, () -> stepFor(associationId, memberId))) {
            case Step.Provision provision -> provisioner.provision(associationId, memberId, provision.contact());
            case Step.Skip ignored -> LOG.info("Nothing to resend, the member has activated, is not active or the account is disabled: association={} member={}",
                    associationId, memberId);
            case Step.Send send -> {
                mailer.send(send.account().email(), AccountLinkPurpose.ACTIVATION, send.link());
                LOG.info("Activation link resent: association={} member={} link={}", associationId, memberId, send.link().reference());
            }
        }
    }

    private Step stepFor(AssociationId associationId, MemberId memberId) {
        Member member = members.findById(associationId, memberId).orElse(null);
        if (member == null || !member.isActive() || member.isAnonymised()) {
            return new Step.Skip();
        }
        Membership membership = memberships.findByMember(associationId, memberId).orElse(null);
        if (membership == null) {
            return new Step.Provision(member.email());
        }
        if (membership.isConfirmed()) {
            return new Step.Skip();
        }
        UserAccount user = users.findById(membership.userId()).filter(account -> account.status() == UserStatus.ACTIVE).orElse(null);
        if (user == null) {
            return new Step.Skip();
        }
        return new Step.Send(user, links.issue(user, membership, AccountLinkPurpose.ACTIVATION));
    }
}
