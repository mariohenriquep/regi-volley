package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.infrastructure.security.SecurityAccount;
import com.regivolley.api.infrastructure.security.SecurityAccountLookup;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.application.identity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceTest
class SecurityAccountLookupAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private SecurityAccountLookup lookup;
    @Autowired
    private UserAccountStore users;
    @Autowired
    private MembershipStore memberships;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private MemberRepository members;
    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsTheStoredFactsOfTheMembershipIncludingItsTenantAndMember() {
        // Arrange
        Association association = associations.save(Fixtures.association());
        Member member = members.save(Fixtures.member(association, "Ana"));
        UserAccount user = users.insert(CredentialsFixtures.user());
        memberships.insert(CredentialsFixtures.confirmed(user, association, member));
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<SecurityAccount> account = lookup.find(user.id(), association.id(), member.id());

        // Assert
        assertThat(account).hasValueSatisfying(found -> {
            assertThat(found.userStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(found.securityStamp()).isEqualTo(user.securityStamp());
            assertThat(found.membershipStatus()).isEqualTo(MembershipStatus.CONFIRMED);
            assertThat(found.associationId()).isEqualTo(association.id());
            assertThat(found.memberId()).isEqualTo(member.id());
        });
    }

    @Test
    void aPendingMembershipIsReturnedAsPendingForTheResolverToRefuse() {
        // Arrange
        Association association = associations.save(Fixtures.association());
        Member member = members.save(Fixtures.member(association, "Ana"));
        UserAccount user = users.insert(CredentialsFixtures.user());
        memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<SecurityAccount> account = lookup.find(user.id(), association.id(), member.id());

        // Assert
        assertThat(account).hasValueSatisfying(found -> assertThat(found.membershipStatus()).isEqualTo(MembershipStatus.PENDING));
    }

    @Test
    void aMembershipCannotResolveAMemberOfAnotherAssociation() {
        // Arrange - tenant isolation: the user belongs to A's member; B's tenant id or B's member id finds nothing
        Association a = associations.save(Fixtures.association());
        Association b = associations.save(Fixtures.association());
        Member memberOfA = members.save(Fixtures.member(a, "Ana"));
        Member memberOfB = members.save(Fixtures.member(b, "Bruno"));
        UserAccount user = users.insert(CredentialsFixtures.user());
        memberships.insert(CredentialsFixtures.confirmed(user, a, memberOfA));
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<SecurityAccount> wrongTenant = lookup.find(user.id(), b.id(), memberOfA.id());
        Optional<SecurityAccount> otherTenantsMember = lookup.find(user.id(), a.id(), memberOfB.id());
        Optional<SecurityAccount> bothOfB = lookup.find(user.id(), b.id(), memberOfB.id());

        // Assert
        assertThat(wrongTenant).isEmpty();
        assertThat(otherTenantsMember).isEmpty();
        assertThat(bothOfB).isEmpty();
    }

    @Test
    void aUserWithoutAMembershipOrAnUnknownUserResolvesToNothing() {
        // Arrange
        Association association = associations.save(Fixtures.association());
        Member member = members.save(Fixtures.member(association, "Ana"));
        UserAccount noMembership = users.insert(CredentialsFixtures.user());
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<SecurityAccount> withoutMembership = lookup.find(noMembership.id(), association.id(), member.id());
        Optional<SecurityAccount> unknown = lookup.find(UUID.randomUUID(), association.id(), member.id());

        // Assert
        assertThat(withoutMembership).isEmpty();
        assertThat(unknown).isEmpty();
    }

    @Test
    void aSomebodyElsesMembershipOfTheSameMemberDoesNotResolveForThisUser() {
        // Arrange
        Association association = associations.save(Fixtures.association());
        Member member = members.save(Fixtures.member(association, "Ana"));
        UserAccount owner = users.insert(CredentialsFixtures.user());
        UserAccount stranger = users.insert(CredentialsFixtures.user());
        memberships.insert(CredentialsFixtures.confirmed(owner, association, member));
        entityManager.flush();
        entityManager.clear();

        // Act
        Optional<SecurityAccount> asStranger = lookup.find(stranger.id(), association.id(), member.id());

        // Assert
        assertThat(asStranger).isEmpty();
        assertThat(new Membership(UUID.randomUUID(), owner.id(), association.id(), member.id(), MembershipStatus.PENDING,
                CredentialsFixtures.NOW, null).isConfirmed()).isFalse();
    }
}
