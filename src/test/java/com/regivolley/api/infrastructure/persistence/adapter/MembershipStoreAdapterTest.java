package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.UserAccountStore;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class MembershipStoreAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant LATER = CredentialsFixtures.NOW.plusSeconds(60);

    @Autowired
    private MembershipStore memberships;
    @Autowired
    private UserAccountStore users;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private MemberRepository members;
    @Autowired
    private EntityManager entityManager;

    private Association association;
    private Member member;
    private UserAccount user;

    private void seed() {
        association = associations.save(Fixtures.association());
        member = members.save(Fixtures.member(association, "Ana"));
        user = users.insert(CredentialsFixtures.user());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void aPendingMembershipComesBackByIdByUserAndByMember() {
        // Arrange
        seed();
        Membership pending = CredentialsFixtures.pending(user, association.id(), member.id());

        // Act
        memberships.insert(pending);
        flushAndClear();

        // Assert
        Membership byId = memberships.findById(pending.id()).orElseThrow();
        assertThat(byId.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(byId.confirmedAt()).isNull();
        assertThat(byId.associationId()).isEqualTo(association.id());
        assertThat(byId.memberId()).isEqualTo(member.id());
        assertThat(memberships.findByUser(user.id())).extracting(Membership::id).containsExactly(pending.id());
        assertThat(memberships.findByUserAndAssociation(user.id(), association.id())).isPresent();
        assertThat(memberships.findByMember(association.id(), member.id())).isPresent();
    }

    @Test
    void confirmingMovesItToConfirmedOnceAndRecordsWhen() {
        // Arrange
        seed();
        Membership pending = memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        flushAndClear();

        // Act
        boolean first = memberships.confirm(pending.id(), LATER);
        boolean second = memberships.confirm(pending.id(), LATER.plusSeconds(5));
        flushAndClear();

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        Membership confirmed = memberships.findById(pending.id()).orElseThrow();
        assertThat(confirmed.status()).isEqualTo(MembershipStatus.CONFIRMED);
        assertThat(confirmed.confirmedAt()).isEqualTo(LATER);
    }

    @Test
    void aMemberOfAnotherAssociationIsNeverFoundThroughThisOne() {
        // Arrange - tenant isolation: the same member id asked for under association B is absent
        seed();
        memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        Association other = associations.save(Fixtures.association());
        flushAndClear();

        // Act
        var viaOtherTenant = memberships.findByMember(other.id(), member.id());
        var userViaOtherTenant = memberships.findByUserAndAssociation(user.id(), other.id());

        // Assert
        assertThat(viaOtherTenant).isEmpty();
        assertThat(userViaOtherTenant).isEmpty();
    }

    @Test
    void theDatabaseKeepsTheMemberInsideItsAssociation() {
        // Arrange - composite foreign key: a membership naming association B with association A's member cannot exist
        seed();
        Association other = associations.save(Fixtures.association());
        Executable act = () -> {
            memberships.insert(CredentialsFixtures.pending(user, other.id(), member.id()));
            entityManager.flush();
        };

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("@");
    }

    @Test
    void aMemberHasAtMostOneMembership() {
        // Arrange
        seed();
        memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        flushAndClear();
        UserAccount another = users.insert(CredentialsFixtures.user());
        Executable act = () -> memberships.insert(CredentialsFixtures.pending(another, association.id(), member.id()));

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("@");
    }

    @Test
    void anAccountHasAtMostOneMembershipPerAssociation() {
        // Arrange
        seed();
        memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        Member second = members.save(Fixtures.member(association, "Rita"));
        flushAndClear();
        Executable act = () -> memberships.insert(CredentialsFixtures.pending(user, association.id(), second.id()));

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("@");
    }

    @Test
    void deletingTheAccountDeletesItsMemberships() {
        // Arrange
        seed();
        Membership pending = memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        flushAndClear();

        // Act
        users.deleteById(user.id());
        flushAndClear();

        // Assert
        assertThat(memberships.findById(pending.id())).isEmpty();
    }

    @Test
    void deletingAMembershipLeavesTheAccount() {
        // Arrange
        seed();
        Membership pending = memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        flushAndClear();

        // Act
        memberships.deleteById(pending.id());
        flushAndClear();

        // Assert
        assertThat(memberships.findById(pending.id())).isEmpty();
        assertThat(users.findById(user.id())).isPresent();
        assertThat(memberships.findById(UUID.randomUUID())).isEmpty();
    }
}
