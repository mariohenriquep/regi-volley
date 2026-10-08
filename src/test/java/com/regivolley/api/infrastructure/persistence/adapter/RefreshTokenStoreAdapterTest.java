package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.UserAccountStore;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class RefreshTokenStoreAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant LATER = CredentialsFixtures.NOW.plusSeconds(60);

    @Autowired
    private RefreshTokenStore tokens;
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
    void aTokenIsFoundByItsHashWithAllItsTimes() {
        // Arrange
        RefreshToken token = CredentialsFixtures.refreshToken(user.id(), membership.id(), UUID.randomUUID(), null);

        // Act
        tokens.insert(token);
        flushAndClear();

        // Assert
        RefreshToken found = tokens.findByTokenHash(token.tokenHash()).orElseThrow();
        assertThat(found).usingRecursiveComparison().isEqualTo(token);
        assertThat(tokens.findByTokenHash(CredentialsFixtures.hash())).isEmpty();
    }

    @Test
    void aTokenCanBeMarkedUsedExactlyOnce() {
        // Arrange
        RefreshToken token = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), UUID.randomUUID(), null));
        flushAndClear();

        // Act
        boolean first = tokens.markUsed(token.id(), LATER);
        boolean second = tokens.markUsed(token.id(), LATER.plusSeconds(1));
        flushAndClear();

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(tokens.findByTokenHash(token.tokenHash()).orElseThrow().usedAt()).isEqualTo(LATER);
    }

    @Test
    void aRevokedTokenCannotBeMarkedUsed() {
        // Arrange
        UUID family = UUID.randomUUID();
        RefreshToken token = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), family, null));
        tokens.revokeFamily(family, LATER);
        flushAndClear();

        // Act
        boolean used = tokens.markUsed(token.id(), LATER);

        // Assert
        assertThat(used).isFalse();
    }

    @Test
    void revokingAFamilyRevokesOnlyThatFamily() {
        // Arrange
        UUID family = UUID.randomUUID();
        RefreshToken a = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), family, null));
        RefreshToken b = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), family, a.id()));
        RefreshToken elsewhere = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), UUID.randomUUID(), null));
        flushAndClear();

        // Act
        int revoked = tokens.revokeFamily(family, LATER);
        flushAndClear();

        // Assert
        assertThat(revoked).isEqualTo(2);
        assertThat(tokens.findByTokenHash(a.tokenHash()).orElseThrow().revokedAt()).isEqualTo(LATER);
        assertThat(tokens.findByTokenHash(b.tokenHash()).orElseThrow().revokedAt()).isEqualTo(LATER);
        assertThat(tokens.findByTokenHash(elsewhere.tokenHash()).orElseThrow().revokedAt()).isNull();
    }

    @Test
    void revokingAllOfAUserCoversEveryFamilyButNotAnotherUsers() {
        // Arrange
        RefreshToken mine1 = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), UUID.randomUUID(), null));
        RefreshToken mine2 = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), UUID.randomUUID(), null));
        Association association = associations.save(Fixtures.association());
        Member member = members.save(Fixtures.member(association, "Rita"));
        UserAccount other = users.insert(CredentialsFixtures.user());
        Membership otherMembership = memberships.insert(CredentialsFixtures.pending(other, association.id(), member.id()));
        RefreshToken theirs = tokens.insert(CredentialsFixtures.refreshToken(other.id(), otherMembership.id(), UUID.randomUUID(), null));
        flushAndClear();

        // Act
        int revoked = tokens.revokeAllOf(user.id(), LATER);
        flushAndClear();

        // Assert
        assertThat(revoked).isEqualTo(2);
        assertThat(tokens.findByTokenHash(mine1.tokenHash()).orElseThrow().isRevoked()).isTrue();
        assertThat(tokens.findByTokenHash(mine2.tokenHash()).orElseThrow().isRevoked()).isTrue();
        assertThat(tokens.findByTokenHash(theirs.tokenHash()).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void expiredTokensOfTheUserAreDeletedAndLiveOnesKept() {
        // Arrange
        RefreshToken live = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), UUID.randomUUID(), null));
        RefreshToken idleDead = tokens.insert(new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), user.id(), membership.id(),
                CredentialsFixtures.hash(), null, CredentialsFixtures.NOW.minus(Duration.ofDays(40)),
                CredentialsFixtures.NOW.plus(Duration.ofDays(50)), CredentialsFixtures.NOW.minus(Duration.ofDays(10)), null, null));
        flushAndClear();

        // Act
        int deleted = tokens.deleteExpiredOf(user.id(), CredentialsFixtures.NOW);
        flushAndClear();

        // Assert
        assertThat(deleted).isEqualTo(1);
        assertThat(tokens.findByTokenHash(idleDead.tokenHash())).isEmpty();
        assertThat(tokens.findByTokenHash(live.tokenHash())).isPresent();
    }

    @Test
    void deletingTheAccountDeletesItsTokensAndASuccessorKeepsItsParentReferenceUntilThen() {
        // Arrange
        RefreshToken parent = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), UUID.randomUUID(), null));
        RefreshToken child = tokens.insert(CredentialsFixtures.refreshToken(user.id(), membership.id(), parent.familyId(), parent.id()));
        flushAndClear();
        assertThat(tokens.findByTokenHash(child.tokenHash()).orElseThrow().parentId()).isEqualTo(parent.id());

        // Act
        users.deleteById(user.id());
        flushAndClear();

        // Assert
        assertThat(tokens.findByTokenHash(parent.tokenHash())).isEmpty();
        assertThat(tokens.findByTokenHash(child.tokenHash())).isEmpty();
    }

    @Test
    void aTokenCannotNameAnotherUsersMembership() {
        // Arrange - the composite foreign key (membership, user) ties the token's membership to the token's own user
        UserAccount stranger = users.insert(CredentialsFixtures.user());
        flushAndClear();
        Executable act = () -> tokens.insert(CredentialsFixtures.refreshToken(stranger.id(), membership.id(), UUID.randomUUID(), null));

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }
}
