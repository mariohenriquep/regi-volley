package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Credential-side objects for the persistence tests. */
final class CredentialsFixtures {

    static final Instant NOW = Fixtures.NOW;

    private CredentialsFixtures() {
    }

    static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    static UserAccount user() {
        return UserAccount.create(EmailAddress.of(Fixtures.unique("user") + "@example.com"), (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 43), NOW);
    }

    static UserAccount userWithPassword() {
        UserAccount account = user();
        return new UserAccount(account.id(), account.email(), UserStatus.ACTIVE, "{argon2id}hash", account.securityStamp(), NOW, NOW);
    }

    static Membership confirmed(UserAccount user, Association association, Member member) {
        return new Membership(UUID.randomUUID(), user.id(), association.id(), member.id(), MembershipStatus.CONFIRMED, NOW, NOW);
    }

    static Membership pending(UserAccount user, AssociationId association, MemberId member) {
        return Membership.pending(user.id(), association, member, NOW);
    }

    static RefreshToken refreshToken(UUID userId, UUID membershipId, UUID familyId, UUID parentId) {
        return new RefreshToken(UUID.randomUUID(), familyId, userId, membershipId, hash(), parentId, NOW,
                NOW.plus(Duration.ofDays(90)), NOW.plus(Duration.ofDays(30)), null, null);
    }

    static EmailLink activationLink(UUID userId, UUID membershipId) {
        return new EmailLink(UUID.randomUUID(), userId, membershipId, AccountLinkPurpose.ACTIVATION, hash(), NOW,
                NOW.plus(Duration.ofDays(7)), null);
    }

    static EmailLink resetLink(UUID userId) {
        return new EmailLink(UUID.randomUUID(), userId, null, AccountLinkPurpose.PASSWORD_RESET, hash(), NOW,
                NOW.plus(Duration.ofMinutes(30)), null);
    }
}
