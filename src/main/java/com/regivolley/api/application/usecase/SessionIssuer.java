package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.AccessToken;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.identity.RefreshTokenPolicy;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.AccessTokenIssuer;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.PrincipalVerifier;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.application.result.SessionTokens;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The session mechanics shared by login and refresh (threat model D-7): starting a family of refresh tokens and rotating a token
 * inside one. A refresh token is opaque (256 random bits), stored only as a hash, and valid 30 days idle and 90 days from the login.
 *
 * <p><b>Reuse detection.</b> Presenting a token that was already rotated means two parties hold it. Within
 * {@link RefreshTokenPolicy#REUSE_GRACE} that is a second browser tab sending the same cookie a moment apart: refused, nothing
 * revoked. Later it is treated as theft: the whole family is revoked and a security event is logged. Two requests racing to rotate
 * the same token are decided by the database ({@link RefreshTokenStore#markUsed}); the loser is treated like the grace case.
 * Every rotation re-asks the {@link PrincipalVerifier}, so a deactivated member cannot refresh and loses the family.
 *
 * <p>Both methods must run inside the caller's transaction. {@link #rotate} answers empty instead of throwing, so the revocations
 * it decided on commit with the transaction; the use case throws afterwards.
 */
final class SessionIssuer {

    private static final Logger LOG = LoggerFactory.getLogger(SessionIssuer.class);

    private final RefreshTokenStore tokens;
    private final UserAccountStore users;
    private final MembershipStore memberships;
    private final PrincipalVerifier verifier;
    private final AccessTokenIssuer accessTokens;
    private final SecretGenerator secrets;
    private final Clock clock;

    SessionIssuer(RefreshTokenStore tokens, UserAccountStore users, MembershipStore memberships, PrincipalVerifier verifier,
                  AccessTokenIssuer accessTokens, SecretGenerator secrets, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.memberships = memberships;
        this.verifier = verifier;
        this.accessTokens = accessTokens;
        this.secrets = secrets;
        this.clock = clock;
    }

    /** Starts a new family for a user who just proved their password: first refresh token plus an access token. */
    SessionTokens open(UserAccount user, Membership membership) {
        Instant now = clock.instant();
        tokens.deleteExpiredOf(user.id(), now);
        String raw = secrets.newSecret();
        RefreshToken first = tokens.insert(RefreshToken.first(user.id(), membership.id(), secrets.hash(raw), now));
        return sessionOf(user, membership, first, raw);
    }

    /** One rotation. Empty means refused; any revocation decided on is committed with the caller's transaction. */
    Optional<SessionTokens> rotate(String rawToken) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = tokens.findByTokenHash(secrets.hash(rawToken));
        if (found.isEmpty()) {
            LOG.info("Refresh refused: reason=UNKNOWN_TOKEN");
            return Optional.empty();
        }
        RefreshToken token = found.get();
        if (token.isRevoked() || token.isExpiredAt(now)) {
            LOG.info("Refresh refused: reason={} userId={} family={}", token.isRevoked() ? "REVOKED" : "EXPIRED", token.userId(), token.familyId());
            return Optional.empty();
        }
        if (token.isUsed()) {
            return reuse(token, now);
        }
        if (!tokens.markUsed(token.id(), now)) {
            LOG.info("Refresh refused: reason=LOST_ROTATION_RACE userId={} family={}", token.userId(), token.familyId());
            return Optional.empty();
        }
        return successor(token, now);
    }

    private Optional<SessionTokens> reuse(RefreshToken token, Instant now) {
        if (now.isBefore(token.usedAt().plus(RefreshTokenPolicy.REUSE_GRACE))) {
            LOG.info("Refresh refused: reason=REUSE_WITHIN_GRACE userId={} family={}", token.userId(), token.familyId());
            return Optional.empty();
        }
        int revoked = tokens.revokeFamily(token.familyId(), now);
        LOG.warn("Security event: refresh token reuse, family revoked: userId={} family={} revokedTokens={}",
                token.userId(), token.familyId(), revoked);
        return Optional.empty();
    }

    private Optional<SessionTokens> successor(RefreshToken token, Instant now) {
        Optional<UserAccount> user = users.findById(token.userId());
        Optional<Membership> membership = memberships.findById(token.membershipId());
        if (user.isEmpty() || membership.isEmpty()) {
            tokens.revokeFamily(token.familyId(), now);
            LOG.info("Refresh refused: reason=ACCOUNT_GONE userId={} family={}", token.userId(), token.familyId());
            return Optional.empty();
        }
        Optional<String> rejection = verifier.rejectionReason(user.get().id(), membership.get().associationId(),
                membership.get().memberId(), user.get().securityStamp());
        if (rejection.isPresent()) {
            tokens.revokeFamily(token.familyId(), now);
            LOG.info("Refresh refused: reason={} userId={} family={}", rejection.get(), token.userId(), token.familyId());
            return Optional.empty();
        }
        String raw = secrets.newSecret();
        RefreshToken next = tokens.insert(token.successor(secrets.hash(raw), now));
        return Optional.of(sessionOf(user.get(), membership.get(), next, raw));
    }

    private SessionTokens sessionOf(UserAccount user, Membership membership, RefreshToken stored, String rawRefreshToken) {
        AccessToken access = accessTokens.issue(user.id(), membership.associationId(), membership.memberId(), user.securityStamp());
        Instant end = stored.idleExpiresAt().isBefore(stored.expiresAt()) ? stored.idleExpiresAt() : stored.expiresAt();
        long expiresIn = Duration.between(clock.instant(), access.expiresAt()).toSeconds();
        return new SessionTokens(access.value(), expiresIn, rawRefreshToken, end);
    }
}
