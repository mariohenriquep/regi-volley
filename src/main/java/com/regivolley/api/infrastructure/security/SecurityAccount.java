package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/**
 * The security facts about one user's membership, as stored: the account status, the current security stamp, the membership
 * status and the association and member the membership is for. {@link PrincipalResolver} compares them with the token on every
 * request; asserting the stored tenant and member equal the token's keeps a lookup (or a join) that answered with another
 * tenant's row from ever being trusted.
 */
public record SecurityAccount(UserStatus userStatus, String securityStamp, MembershipStatus membershipStatus,
                              AssociationId associationId, MemberId memberId) {

    public SecurityAccount {
        Objects.requireNonNull(userStatus, "userStatus must not be null");
        Objects.requireNonNull(securityStamp, "securityStamp must not be null");
        Objects.requireNonNull(membershipStatus, "membershipStatus must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
    }

    @Override
    public String toString() {
        return "SecurityAccount{userStatus=%s, membershipStatus=%s}".formatted(userStatus, membershipStatus);
    }
}
