package com.regivolley.api.application.usecase;

import com.regivolley.api.application.exception.InvalidLinkException;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.CommonPasswordList;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consuming an emailed link (threat model D-11), shared by activation and password reset: activation sets the first password and
 * confirms the membership, a reset sets a new password. Either way the mailbox has been proved, so the security stamp changes and
 * every refresh token is revoked: whoever held a session before the password changed is out, immediately.
 *
 * <p>The link is looked up by the hash of its token and must be of the right purpose, unconsumed and unexpired; for any other
 * state the answer is the same {@link InvalidLinkException}. The password policy is checked <em>before</em> the link is spent, so
 * a weak password costs the person a retry, not the link. The link is then consumed with a conditional update, so of two parallel
 * uses exactly one wins. Nothing is logged but ids and the purpose.
 */
final class CredentialLinkConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialLinkConsumer.class);

    private final EmailLinkStore links;
    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final AssociationRepository associations;
    private final PasswordPolicy policy;
    private final PasswordHasher hasher;
    private final RefreshTokenStore refreshTokens;
    private final SecretGenerator secrets;
    private final TransactionRunner transactions;
    private final Clock clock;

    CredentialLinkConsumer(EmailLinkStore links, UserAccountStore users, MembershipStore memberships, AssociationRepository associations,
                           CommonPasswordList commonPasswords, PasswordHasher hasher, RefreshTokenStore refreshTokens,
                           SecretGenerator secrets, TransactionRunner transactions, Clock clock) {
        this.links = links;
        this.users = users;
        this.memberships = memberships;
        this.associations = associations;
        this.policy = new PasswordPolicy(commonPasswords);
        this.hasher = hasher;
        this.refreshTokens = refreshTokens;
        this.secrets = secrets;
        this.transactions = transactions;
        this.clock = clock;
    }

    void consume(String token, String password, AccountLinkPurpose purpose) {
        if (token == null || token.isBlank()) {
            throw new InvalidLinkException();
        }
        Instant now = clock.instant();
        EmailLink link = links.findByTokenHash(secrets.hash(token))
                .filter(found -> found.purpose() == purpose && found.isUsableAt(now))
                .orElseThrow(InvalidLinkException::new);
        UserAccount user = users.findById(link.userId())
                .filter(found -> found.status() == UserStatus.ACTIVE)
                .orElseThrow(InvalidLinkException::new);

        policy.check(password, user.email(), associationNamesOf(user));
        String hash = hasher.hash(password);

        transactions.inNewTransaction(() -> {
            if (!links.consume(link.id(), now)) {
                throw new InvalidLinkException();
            }
            users.setPassword(user.id(), hash, secrets.newSecret(), now);
            if (purpose == AccountLinkPurpose.ACTIVATION) {
                memberships.confirm(link.membershipId(), now);
            }
            int revoked = refreshTokens.revokeAllOf(user.id(), now);
            LOG.info("Link consumed: purpose={} userId={} link={} revokedTokens={}", purpose, user.id(), link.id(), revoked);
            return null;
        });
    }

    private List<String> associationNamesOf(UserAccount user) {
        return memberships.findByUser(user.id()).stream()
                .map(membership -> associations.findById(membership.associationId()))
                .flatMap(Optional::stream)
                .map(Association::name)
                .toList();
    }
}
