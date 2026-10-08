package com.regivolley.api.infrastructure.security;

import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Turns a verified token into an {@link AuthenticatedActor}, on every request (threat model D-5, D-6; closes gap G1).
 * A valid signature is not enough: the account must exist and be enabled, its security stamp must still be the one in the token,
 * the membership must be confirmed, and the member must exist in that association, be active and not be anonymised.
 * Any failure is a {@link PrincipalRejectedException}, which the entry point turns into the one constant 401. There is
 * deliberately no cache: deactivation, erasure and password change take effect on the next request.
 *
 * <p>Account facts come through {@link SecurityAccountLookup} (implemented by 26b); the member through the tenant-scoped
 * {@link MemberRepository}. Roles are never read here nor taken from the token (D-5): use cases check them.
 * Logs ids and the reason, never claims or names.
 */
public class PrincipalResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PrincipalResolver.class);

    private final SecurityAccountLookup accounts;
    private final MemberRepository members;

    public PrincipalResolver(SecurityAccountLookup accounts, MemberRepository members) {
        this.accounts = accounts;
        this.members = members;
    }

    public AuthenticatedActor resolve(Jwt jwt) {
        UUID userId = uuid(jwt.getSubject());
        UUID tenant = uuid(jwt.getClaim(AccessTokenPolicy.CLAIM_ASSOCIATION));
        UUID memberUuid = uuid(jwt.getClaim(AccessTokenPolicy.CLAIM_MEMBER));
        Object stamp = jwt.getClaim(AccessTokenPolicy.CLAIM_SECURITY_STAMP);
        if (userId == null || tenant == null || memberUuid == null || !(stamp instanceof String tokenStamp)) {
            throw reject(PrincipalRejection.MALFORMED_CLAIMS, userId, tenant, memberUuid);
        }
        AssociationId associationId = AssociationId.of(tenant);
        MemberId memberId = MemberId.of(memberUuid);

        SecurityAccount account = accounts.find(userId, associationId, memberId)
                .orElseThrow(() -> reject(PrincipalRejection.UNKNOWN_ACCOUNT, userId, tenant, memberUuid));
        if (account.userStatus() != UserStatus.ACTIVE) {
            throw reject(PrincipalRejection.ACCOUNT_DISABLED, userId, tenant, memberUuid);
        }
        if (!sameStamp(account.securityStamp(), tokenStamp)) {
            throw reject(PrincipalRejection.STAMP_MISMATCH, userId, tenant, memberUuid);
        }
        if (account.membershipStatus() != MembershipStatus.CONFIRMED) {
            throw reject(PrincipalRejection.MEMBERSHIP_NOT_CONFIRMED, userId, tenant, memberUuid);
        }

        Member member = members.findById(associationId, memberId)
                .filter(found -> found.associationId().equals(associationId) && found.id().equals(memberId))
                .orElseThrow(() -> reject(PrincipalRejection.MEMBER_NOT_FOUND, userId, tenant, memberUuid));
        if (member.isAnonymised()) {
            throw reject(PrincipalRejection.MEMBER_ANONYMISED, userId, tenant, memberUuid);
        }
        if (!member.isActive()) {
            throw reject(PrincipalRejection.MEMBER_INACTIVE, userId, tenant, memberUuid);
        }
        return new AuthenticatedActor(userId, associationId, memberId);
    }

    private static PrincipalRejectedException reject(PrincipalRejection reason, UUID userId, UUID associationId, UUID memberId) {
        LOG.info("Token rejected: reason={} userId={} associationId={} memberId={}", reason, userId, associationId, memberId);
        return new PrincipalRejectedException(reason);
    }

    private static boolean sameStamp(String stored, String presented) {
        return MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    private static UUID uuid(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
