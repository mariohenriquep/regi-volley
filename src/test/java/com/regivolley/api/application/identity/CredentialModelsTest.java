package com.regivolley.api.application.identity;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The small immutable models behind the credential tables: invariants, lifetimes, and nothing secret in toString. */
class CredentialModelsTest {

    private static final Instant NOW = Instant.parse("2026-10-12T09:00:00Z");

    @Test
    void aNewAccountIsEnabledHasNoPasswordAndA256BitStamp() {
        // Arrange
        EmailAddress email = EmailAddress.of("ana@example.com");

        // Act
        UserAccount account = UserAccount.create(email, "s".repeat(43), NOW);

        // Assert
        assertThat(account.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(account.hasPassword()).isFalse();
        assertThat(account.securityStamp()).isEqualTo("s".repeat(43));
        assertThat(account.createdAt()).isEqualTo(NOW).isEqualTo(account.updatedAt());
    }

    @Test
    void anAccountNeverPrintsItsEmailHashOrStamp() {
        // Arrange
        UserAccount account = new UserAccount(UUID.randomUUID(), EmailAddress.of("ana@example.com"), UserStatus.ACTIVE, "{argon2id}secret-hash",
                "secret-stamp", NOW, NOW);

        // Act
        String text = account.toString();

        // Assert
        assertThat(text).contains(account.id().toString()).doesNotContain("ana").doesNotContain("secret");
    }

    @Test
    void aNewMembershipIsPendingWithoutAConfirmationTime() {
        // Arrange
        UUID user = UUID.randomUUID();

        // Act
        Membership pending = Membership.pending(user, AssociationId.generate(), MemberId.generate(), NOW);

        // Assert
        assertThat(pending.isConfirmed()).isFalse();
        assertThat(pending.confirmedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(MembershipStatus.class)
    void theConfirmationTimeMustMatchTheStatus(MembershipStatus status) {
        // Arrange - a time on a pending membership, or none on a confirmed one
        Instant wrongTime = status == MembershipStatus.PENDING ? NOW : null;
        Executable act = () -> new Membership(UUID.randomUUID(), UUID.randomUUID(), AssociationId.generate(), MemberId.generate(), status, NOW, wrongTime);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("confirmation time");
    }

    @Test
    void aFirstRefreshTokenGetsTheIdleAndAbsoluteLifetimes() {
        // Arrange
        UUID user = UUID.randomUUID();
        UUID membership = UUID.randomUUID();

        // Act
        RefreshToken first = RefreshToken.first(user, membership, "hash", NOW);

        // Assert
        assertThat(first.idleExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(first.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(90)));
        assertThat(first.isUsed()).isFalse();
        assertThat(first.isRevoked()).isFalse();
        assertThat(first.isExpiredAt(NOW)).isFalse();
        assertThat(first.isExpiredAt(NOW.plus(Duration.ofDays(30)))).as("idle end is exclusive").isTrue();
        assertThat(first.toString()).doesNotContain("hash");
    }

    @Test
    void aSuccessorKeepsTheFamilyAndTheAbsoluteEndAndNeverOutlivesIt() {
        // Arrange
        RefreshToken first = RefreshToken.first(UUID.randomUUID(), UUID.randomUUID(), "h1", NOW);
        Instant lateRotation = NOW.plus(Duration.ofDays(80));

        // Act
        RefreshToken next = first.successor("h2", lateRotation);

        // Assert
        assertThat(next.familyId()).isEqualTo(first.familyId());
        assertThat(next.parentId()).isEqualTo(first.id());
        assertThat(next.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(next.idleExpiresAt()).isEqualTo(first.expiresAt());
        assertThat(first.successor("h3", NOW.plus(Duration.ofDays(1))).idleExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(31)));
    }

    @ParameterizedTest
    @EnumSource(AccountLinkPurpose.class)
    void anActivationLinkNamesItsMembershipAndAResetLinkDoesNot(AccountLinkPurpose purpose) {
        // Arrange - the wrong combination for each purpose
        UUID membership = purpose == AccountLinkPurpose.ACTIVATION ? null : UUID.randomUUID();
        Executable act = () -> new EmailLink(UUID.randomUUID(), UUID.randomUUID(), membership, purpose, "h", NOW, NOW.plusSeconds(60), null);

        // Act
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("activation link");
    }

    @Test
    void aLinkIsUsableOnlyBeforeItsExpiryAndWhileUnconsumed() {
        // Arrange
        EmailLink open = new EmailLink(UUID.randomUUID(), UUID.randomUUID(), null, AccountLinkPurpose.PASSWORD_RESET, "secret-hash", NOW,
                NOW.plusSeconds(60), null);
        EmailLink spent = new EmailLink(open.id(), open.userId(), null, open.purpose(), "h", NOW, NOW.plusSeconds(60), NOW);

        // Act
        boolean justBefore = open.isUsableAt(NOW.plusSeconds(59));
        boolean atExpiry = open.isUsableAt(NOW.plusSeconds(60));
        boolean consumed = spent.isUsableAt(NOW);

        // Assert
        assertThat(justBefore).isTrue();
        assertThat(atExpiry).isFalse();
        assertThat(consumed).isFalse();
        assertThat(open.toString()).doesNotContain("secret-hash");
    }
}
