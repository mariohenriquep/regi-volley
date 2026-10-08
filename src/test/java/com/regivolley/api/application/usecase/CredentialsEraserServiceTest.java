package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.domain.model.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Section 6 of the threat model: erasing a member deletes their credentials, and the account too when it was the only membership. */
class CredentialsEraserServiceTest {

    private CredentialsHarness h;
    private CredentialsEraserService eraser;

    @BeforeEach
    void setUp() {
        h = new CredentialsHarness();
        eraser = new CredentialsEraserService(h.stores.users, h.stores.memberships, h.transactions);
    }

    @Test
    void deletesTheAccountItsTokensAndLinksWhenItWasTheOnlyMembership() {
        // Arrange
        UserAccount user = h.confirmedUser();
        Membership membership = h.membershipOf(user);
        h.stores.refreshTokens.insert(RefreshToken.first(user.id(), membership.id(), "hash-1", CredentialsHarness.NOW));
        h.stores.links.insert(new EmailLink(UUID.randomUUID(), user.id(), null, AccountLinkPurpose.PASSWORD_RESET, "hash-2",
                CredentialsHarness.NOW, CredentialsHarness.NOW.plusSeconds(60), null));

        // Act
        eraser.erase(h.association.id(), h.member.id());

        // Assert
        assertThat(h.stores.users.byId).isEmpty();
        assertThat(h.stores.memberships.byId).isEmpty();
        assertThat(h.stores.refreshTokens.byId).isEmpty();
        assertThat(h.stores.links.byId).isEmpty();
    }

    @Test
    void keepsTheAccountWhenItHasAnotherMembership() {
        // Arrange
        UserAccount user = h.confirmedUser();
        var otherAssociation = Data.association();
        Member otherMember = Data.member(otherAssociation);
        Membership other = h.stores.memberships.insert(new Membership(UUID.randomUUID(), user.id(), otherAssociation.id(), otherMember.id(),
                MembershipStatus.CONFIRMED, CredentialsHarness.NOW, CredentialsHarness.NOW));

        // Act
        eraser.erase(h.association.id(), h.member.id());

        // Assert
        assertThat(h.stores.users.byId).containsKey(user.id());
        assertThat(h.stores.memberships.byId).containsOnlyKeys(other.id());
    }

    @Test
    void erasingAMemberWithoutCredentialsChangesNothing() {
        // Arrange
        UserAccount unrelated = h.confirmedUser();
        Member stranger = Data.member(h.association);

        // Act
        eraser.erase(h.association.id(), stranger.id());

        // Assert
        assertThat(h.stores.users.byId).containsKey(unrelated.id());
        assertThat(h.stores.memberships.byId).hasSize(1);
    }

    @Test
    void anotherTenantsMemberIdDoesNotReachThisTenantsCredentials() {
        // Arrange - the same member id asked for under another association finds nothing
        UserAccount user = h.confirmedUser();

        // Act
        eraser.erase(Data.association().id(), h.member.id());

        // Assert
        assertThat(h.stores.users.byId).containsKey(user.id());
    }
}
