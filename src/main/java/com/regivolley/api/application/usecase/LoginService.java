package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LoginCommand;
import com.regivolley.api.application.exception.InvalidCredentialsException;
import com.regivolley.api.application.identity.ClientAddresses;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.AccessTokenIssuer;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.port.PrincipalVerifier;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.application.result.SessionTokens;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Email and password to a session (threat model D-8, D-10, L1, L2). Whatever goes wrong - unknown email, a malformed one, an
 * account never activated, a wrong password, a disabled account, a membership still pending, an inactive member, more than one
 * membership - the caller gets the same {@link InvalidCredentialsException}, and exactly one hash is verified (a precomputed
 * dummy one when there is no password to check), so neither the answer nor the time says which. The real reason goes to the
 * audit log, by id, with the client address truncated.
 *
 * <p>The per-email throttle is taken first, before any hashing and for unknown emails too. A legacy hash that verifies is replaced by
 * the current format, but only if the stored hash is still the one that was verified (a concurrent reset must not be overwritten).
 *
 * <p>MVP: exactly one CONFIRMED membership can sign in. With more the login fails closed until a selection step exists.
 */
@Service
public class LoginService implements LoginUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LoginService.class);

    private enum Failure {
        UNKNOWN_ACCOUNT, NOT_ACTIVATED, WRONG_PASSWORD, ACCOUNT_DISABLED, NO_CONFIRMED_MEMBERSHIP, MULTIPLE_MEMBERSHIPS
    }

    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final PasswordHasher hasher;
    private final PrincipalVerifier verifier;
    private final AttemptThrottle throttle;
    private final SessionIssuer sessions;
    private final TransactionRunner transactions;
    private final Clock clock;

    public LoginService(UserAccountStore users, MembershipStore memberships, PasswordHasher hasher, PrincipalVerifier verifier,
                        AttemptThrottle throttle, RefreshTokenStore refreshTokens, AccessTokenIssuer accessTokens,
                        SecretGenerator secrets, TransactionRunner transactions, Clock clock) {
        this.users = users;
        this.memberships = memberships;
        this.hasher = hasher;
        this.verifier = verifier;
        this.throttle = throttle;
        this.sessions = new SessionIssuer(refreshTokens, users, memberships, verifier, accessTokens, secrets, clock);
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * @throws com.regivolley.api.application.exception.RateLimitExceededException if this email was tried too often
     * @throws InvalidCredentialsException                                          for every other way the login can fail
     */
    @Override
    public SessionTokens execute(LoginCommand command) {
        throttle.checkLogin(command.email());
        String password = command.password();
        Optional<UserAccount> found = parse(command.email()).flatMap(users::findByEmail);

        if (found.isEmpty() || !found.get().hasPassword()) {
            hasher.burn(password);
            throw failed(found.isEmpty() ? Failure.UNKNOWN_ACCOUNT : Failure.NOT_ACTIVATED, found.orElse(null), command);
        }
        UserAccount user = found.get();
        if (!hasher.matches(password, user.passwordHash())) {
            throw failed(Failure.WRONG_PASSWORD, user, command);
        }
        if (user.status() != UserStatus.ACTIVE) {
            throw failed(Failure.ACCOUNT_DISABLED, user, command);
        }
        List<Membership> confirmed = memberships.findByUser(user.id()).stream().filter(Membership::isConfirmed).toList();
        if (confirmed.isEmpty()) {
            throw failed(Failure.NO_CONFIRMED_MEMBERSHIP, user, command);
        }
        if (confirmed.size() > 1) {
            throw failed(Failure.MULTIPLE_MEMBERSHIPS, user, command);
        }
        Membership membership = confirmed.get(0);
        Optional<String> rejection = verifier.rejectionReason(user.id(), membership.associationId(), membership.memberId(), user.securityStamp());
        if (rejection.isPresent()) {
            LOG.info("Login failed: reason={} userId={} ip={}", rejection.get(), user.id(), truncated(command));
            throw new InvalidCredentialsException();
        }

        upgradeHashIfLegacy(user, password);
        SessionTokens tokens = transactions.inNewTransaction(() -> sessions.open(user, membership));
        LOG.info("Login succeeded: userId={} associationId={} memberId={}", user.id(), membership.associationId(), membership.memberId());
        return tokens;
    }

    private void upgradeHashIfLegacy(UserAccount user, String password) {
        if (hasher.needsUpgrade(user.passwordHash())) {
            users.replacePasswordHash(user.id(), user.passwordHash(), hasher.hash(password), clock.instant());
        }
    }

    private static Optional<EmailAddress> parse(String email) {
        try {
            return Optional.of(EmailAddress.of(email));
        } catch (InvalidFieldException e) {
            return Optional.empty();
        }
    }

    private static InvalidCredentialsException failed(Failure reason, UserAccount user, LoginCommand command) {
        LOG.info("Login failed: reason={} userId={} ip={}", reason, user == null ? "-" : user.id(), truncated(command));
        return new InvalidCredentialsException();
    }

    private static String truncated(LoginCommand command) {
        return ClientAddresses.truncate(command.clientAddress());
    }
}
