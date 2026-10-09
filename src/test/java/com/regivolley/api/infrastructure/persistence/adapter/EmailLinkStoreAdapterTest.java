package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.application.exception.LinkAlreadyIssuedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.port.EmailLinkStore;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.UserAccountStore;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class EmailLinkStoreAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant LATER = CredentialsFixtures.NOW.plusSeconds(60);

    @Autowired
    private EmailLinkStore links;
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

    private UserAccount user;
    private Membership membership;

    @BeforeEach
    void seed() {
        Association association = associations.save(Fixtures.association());
        Member member = members.save(Fixtures.member(association, "Ana"));
        user = users.insert(CredentialsFixtures.user());
        membership = memberships.insert(CredentialsFixtures.pending(user, association.id(), member.id()));
        flushAndClear();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void anActivationLinkIsFoundByHashWithItsMembership() {
        // Arrange
        EmailLink link = CredentialsFixtures.activationLink(user.id(), membership.id());

        // Act
        links.insert(link);
        flushAndClear();

        // Assert
        EmailLink found = links.findByTokenHash(link.tokenHash()).orElseThrow();
        assertThat(found).usingRecursiveComparison().isEqualTo(link);
        assertThat(links.findByTokenHash(CredentialsFixtures.hash())).isEmpty();
    }

    @Test
    void aResetLinkBelongsToTheAccountAlone() {
        // Arrange
        EmailLink link = CredentialsFixtures.resetLink(user.id());

        // Act
        links.insert(link);
        flushAndClear();

        // Assert
        EmailLink found = links.findByTokenHash(link.tokenHash()).orElseThrow();
        assertThat(found.membershipId()).isNull();
        assertThat(found.purpose()).isEqualTo(AccountLinkPurpose.PASSWORD_RESET);
    }

    @Test
    void aLinkIsConsumedExactlyOnce() {
        // Arrange
        EmailLink link = links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        flushAndClear();

        // Act
        boolean first = links.consume(link.id(), LATER);
        boolean second = links.consume(link.id(), LATER.plusSeconds(1));
        flushAndClear();

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(links.findByTokenHash(link.tokenHash()).orElseThrow().consumedAt()).isEqualTo(LATER);
    }

    @Test
    void aNewerActivationLinkSupersedesTheOlderOneOfTheSameMembershipOnly() {
        // Arrange
        EmailLink old = links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        EmailLink reset = links.insert(CredentialsFixtures.resetLink(user.id()));
        flushAndClear();

        // Act
        int superseded = links.invalidateOpenForMembership(membership.id(), AccountLinkPurpose.ACTIVATION, LATER);
        flushAndClear();

        // Assert
        assertThat(superseded).isEqualTo(1);
        assertThat(links.findByTokenHash(old.tokenHash()).orElseThrow().consumedAt()).isEqualTo(LATER);
        assertThat(links.findByTokenHash(reset.tokenHash()).orElseThrow().consumedAt()).isNull();
    }

    @Test
    void aNewerResetLinkSupersedesTheOlderOnesOfTheUser() {
        // Arrange
        EmailLink old = links.insert(CredentialsFixtures.resetLink(user.id()));
        EmailLink activation = links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        flushAndClear();

        // Act
        int superseded = links.invalidateOpenForUser(user.id(), AccountLinkPurpose.PASSWORD_RESET, LATER);
        flushAndClear();

        // Assert
        assertThat(superseded).isEqualTo(1);
        assertThat(links.findByTokenHash(old.tokenHash()).orElseThrow().consumedAt()).isEqualTo(LATER);
        assertThat(links.findByTokenHash(activation.tokenHash()).orElseThrow().consumedAt()).isNull();
    }

    @Test
    void spentLinksOfTheUserAreDeletedAndOpenOnesKept() {
        // Arrange
        EmailLink open = links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        EmailLink consumed = links.insert(CredentialsFixtures.resetLink(user.id()));
        links.consume(consumed.id(), CredentialsFixtures.NOW);
        EmailLink expired = links.insert(new EmailLink(java.util.UUID.randomUUID(), user.id(), null, AccountLinkPurpose.PASSWORD_RESET,
                CredentialsFixtures.hash(), CredentialsFixtures.NOW.minus(Duration.ofHours(2)), CredentialsFixtures.NOW.minus(Duration.ofHours(1)), null));
        flushAndClear();

        // Act
        int deleted = links.deleteSpentOf(user.id(), CredentialsFixtures.NOW);
        flushAndClear();

        // Assert
        assertThat(deleted).isEqualTo(2);
        assertThat(links.findByTokenHash(open.tokenHash())).isPresent();
        assertThat(links.findByTokenHash(consumed.tokenHash())).isEmpty();
        assertThat(links.findByTokenHash(expired.tokenHash())).isEmpty();
    }

    @Test
    void deletingTheAccountDeletesItsLinks() {
        // Arrange
        EmailLink link = links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        flushAndClear();

        // Act
        users.deleteById(user.id());
        flushAndClear();

        // Assert
        assertThat(links.findByTokenHash(link.tokenHash())).isEmpty();
    }

    @Test
    void aSecondLiveActivationLinkForTheSameMembershipIsRefusedAsAlreadyIssued() {
        // Arrange
        links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        flushAndClear();
        Executable act = () -> links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));

        // Act
        LinkAlreadyIssuedException ex = assertThrows(LinkAlreadyIssuedException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("@");
    }

    @Test
    void aSecondLiveResetLinkForTheSameUserIsRefusedAsAlreadyIssued() {
        // Arrange
        links.insert(CredentialsFixtures.resetLink(user.id()));
        flushAndClear();
        Executable act = () -> links.insert(CredentialsFixtures.resetLink(user.id()));

        // Act
        LinkAlreadyIssuedException ex = assertThrows(LinkAlreadyIssuedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void aNewLinkIsFineOnceTheOlderOneIsConsumedOrSuperseded() {
        // Arrange
        EmailLink old = links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        links.insert(CredentialsFixtures.resetLink(user.id()));
        links.invalidateOpenForMembership(membership.id(), AccountLinkPurpose.ACTIVATION, LATER);
        links.consume(links.findByTokenHash(old.tokenHash()).orElseThrow().id(), LATER);
        links.invalidateOpenForUser(user.id(), AccountLinkPurpose.PASSWORD_RESET, LATER);
        flushAndClear();

        // Act
        EmailLink newerActivation = links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));
        EmailLink newerReset = links.insert(CredentialsFixtures.resetLink(user.id()));
        flushAndClear();

        // Assert
        assertThat(links.findByTokenHash(newerActivation.tokenHash())).isPresent();
        assertThat(links.findByTokenHash(newerReset.tokenHash())).isPresent();
    }

    @Test
    void aResetLinkAndAnActivationLinkMayBeLiveTogether() {
        // Arrange
        links.insert(CredentialsFixtures.activationLink(user.id(), membership.id()));

        // Act
        EmailLink reset = links.insert(CredentialsFixtures.resetLink(user.id()));
        flushAndClear();

        // Assert
        assertThat(links.findByTokenHash(reset.tokenHash())).isPresent();
    }

    @Test
    void anActivationLinkCannotNameAnotherUsersMembership() {
        // Arrange - the composite foreign key (membership, user) ties the link's membership to the link's own user
        UserAccount stranger = users.insert(CredentialsFixtures.user());
        flushAndClear();
        Executable act = () -> links.insert(CredentialsFixtures.activationLink(stranger.id(), membership.id()));

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("@");
    }
}
