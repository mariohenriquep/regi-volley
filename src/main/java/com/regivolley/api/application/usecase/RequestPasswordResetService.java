package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RequestPasswordResetCommand;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.application.port.BackgroundWork;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * "I forgot my password" (threat model D-10, D-11a). The caller always gets the same answer, so the request thread only takes the
 * per-email throttle (which counts unknown addresses as much as known ones) and hands the rest to {@link BackgroundWork}:
 * whether the address exists, the database writes and the mail all happen off the request, so response time says nothing.
 * Failures there are logged by exception type only.
 *
 * <p>An activated account gets a PASSWORD_RESET link; an account that was never activated gets its ACTIVATION links again
 * instead (a reset would set a password without confirming the membership). Disabled accounts and accounts without a
 * membership get nothing. Links go to the account's own address ({@link UserAccount#email()}).
 */
@Service
public class RequestPasswordResetService implements RequestPasswordResetUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(RequestPasswordResetService.class);

    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final EmailLinkIssuer links;
    private final AccountLinkMailer mailer;
    private final AttemptThrottle throttle;
    private final BackgroundWork background;
    private final TransactionRunner transactions;

    public RequestPasswordResetService(UserAccountStore users, MembershipStore memberships, EmailLinkStore links, SecretGenerator secrets,
                                       AccountLinkMailer mailer, AttemptThrottle throttle, BackgroundWork background,
                                       TransactionRunner transactions, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.links = new EmailLinkIssuer(links, secrets, clock);
        this.mailer = mailer;
        this.throttle = throttle;
        this.background = background;
        this.transactions = transactions;
    }

    /** @throws com.regivolley.api.application.exception.RateLimitExceededException if this email asked too often (the same for addresses that do not exist) */
    @Override
    public Void execute(RequestPasswordResetCommand command) {
        String email = command.email();
        throttle.checkPasswordResetRequest(email);
        background.run(() -> {
            try {
                process(email);
            } catch (RuntimeException e) {
                LOG.warn("A password reset request could not be completed: cause={}", e.getClass().getSimpleName());
            }
        });
        return null;
    }

    private void process(String email) {
        Optional<UserAccount> found = parse(email).flatMap(users::findByEmail);
        if (found.isEmpty() || found.get().status() != UserStatus.ACTIVE) {
            return;
        }
        UserAccount user = found.get();
        List<Membership> all = memberships.findByUser(user.id());
        if (all.isEmpty()) {
            return;
        }
        if (user.hasPassword()) {
            send(user, null, AccountLinkPurpose.PASSWORD_RESET);
        } else {
            all.stream().filter(membership -> !membership.isConfirmed())
                    .forEach(membership -> send(user, membership, AccountLinkPurpose.ACTIVATION));
        }
    }

    private void send(UserAccount user, Membership toConfirm, AccountLinkPurpose purpose) {
        AccountLink link = Conflicts.retrying(transactions, () -> links.issue(user, toConfirm, purpose));
        mailer.send(user.email(), purpose, link);
        LOG.info("Link issued on request: purpose={} userId={} link={}", purpose, user.id(), link.reference());
    }

    private static Optional<EmailAddress> parse(String email) {
        try {
            return Optional.of(EmailAddress.of(email));
        } catch (InvalidFieldException e) {
            return Optional.empty();
        }
    }
}
