package com.regivolley.api.infrastructure.security;

import java.util.Objects;

/**
 * The security facts about one user's membership, as stored: the account status, the current security stamp and the
 * membership status. {@link PrincipalResolver} compares them with the token on every request.
 */
public record SecurityAccount(UserStatus userStatus, String securityStamp, MembershipStatus membershipStatus) {

    public SecurityAccount {
        Objects.requireNonNull(userStatus, "userStatus must not be null");
        Objects.requireNonNull(securityStamp, "securityStamp must not be null");
        Objects.requireNonNull(membershipStatus, "membershipStatus must not be null");
    }

    @Override
    public String toString() {
        return "SecurityAccount{userStatus=%s, membershipStatus=%s}".formatted(userStatus, membershipStatus);
    }
}
