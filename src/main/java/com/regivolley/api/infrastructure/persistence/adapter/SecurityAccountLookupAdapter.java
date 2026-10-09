package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.infrastructure.persistence.entity.UserAccountJpaEntity;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.infrastructure.security.SecurityAccount;
import com.regivolley.api.infrastructure.security.SecurityAccountLookup;
import com.regivolley.api.application.identity.UserStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link SecurityAccountLookup} over {@code membership} and {@code app_user}: two indexed reads per call, no cache, so a
 * password change, a deactivation or an erasure takes effect on the very next request (threat model D-6). The membership is
 * looked up by user, association and member together, so a user's membership of one association never answers for another's;
 * the stored association and member travel back for the resolver to compare with the token.
 */
@Component
public class SecurityAccountLookupAdapter implements SecurityAccountLookup {

    private final MembershipJpaRepository memberships;
    private final UserAccountJpaRepository accounts;

    public SecurityAccountLookupAdapter(MembershipJpaRepository memberships, UserAccountJpaRepository accounts) {
        this.memberships = memberships;
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SecurityAccount> find(UUID userId, AssociationId associationId, MemberId memberId) {
        return memberships.findByUserIdAndAssociationIdAndMemberId(userId, associationId.value(), memberId.value())
                .flatMap(membership -> accounts.findById(userId).map(user -> account(user, membership.getStatus(),
                        membership.getAssociationId(), membership.getMemberId())));
    }

    private static SecurityAccount account(UserAccountJpaEntity user, String membershipStatus, UUID associationId, UUID memberId) {
        return new SecurityAccount(UserStatus.valueOf(user.getStatus()), user.getSecurityStamp(),
                MembershipStatus.valueOf(membershipStatus), AssociationId.of(associationId), MemberId.of(memberId));
    }
}
