package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import com.regivolley.api.application.exception.AccountAlreadyExistsException;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.application.identity.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class UserAccountStoreAdapterTest extends AbstractPostgresIntegrationTest {

    private static final Instant LATER = CredentialsFixtures.NOW.plusSeconds(60);

    @Autowired
    private UserAccountStore users;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void aNewAccountComesBackByIdAndByEmailWithoutAPassword() {
        // Arrange
        UserAccount account = CredentialsFixtures.user();

        // Act
        users.insert(account);
        flushAndClear();

        // Assert
        UserAccount byId = users.findById(account.id()).orElseThrow();
        assertThat(byId.email()).isEqualTo(account.email());
        assertThat(byId.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(byId.hasPassword()).isFalse();
        assertThat(byId.securityStamp()).isEqualTo(account.securityStamp()).hasSize(43);
        assertThat(byId.createdAt()).isEqualTo(CredentialsFixtures.NOW);
        assertThat(users.findByEmail(account.email())).hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(account.id()));
    }

    @Test
    void anUnknownAccountIsAbsent() {
        // Arrange
        UUID unknown = UUID.randomUUID();

        // Act
        boolean present = users.findById(unknown).isPresent();

        // Assert
        assertThat(present).isFalse();
        assertThat(users.findByEmail(EmailAddress.of("nobody@example.com"))).isEmpty();
    }

    @Test
    void theSameEmailTwiceIsRefusedAsAnExistingAccount() {
        // Arrange
        UserAccount first = users.insert(CredentialsFixtures.user());
        UserAccount second = new UserAccount(UUID.randomUUID(), first.email(), UserStatus.ACTIVE, null, "stamp", CredentialsFixtures.NOW,
                CredentialsFixtures.NOW);
        Executable act = () -> users.insert(second);

        // Act
        AccountAlreadyExistsException ex = assertThrows(AccountAlreadyExistsException.class, act);

        // Assert - the message names no address
        assertThat(ex.getMessage()).doesNotContain("@");
    }

    @Test
    void settingThePasswordStoresTheHashAndReplacesTheStampTogether() {
        // Arrange
        UserAccount account = users.insert(CredentialsFixtures.user());

        // Act
        users.setPassword(account.id(), "{argon2id}new-hash", "new-stamp", LATER);
        flushAndClear();

        // Assert
        UserAccount reloaded = users.findById(account.id()).orElseThrow();
        assertThat(reloaded.passwordHash()).isEqualTo("{argon2id}new-hash");
        assertThat(reloaded.securityStamp()).isEqualTo("new-stamp");
        assertThat(reloaded.updatedAt()).isEqualTo(LATER);
        assertThat(reloaded.createdAt()).isEqualTo(CredentialsFixtures.NOW);
    }

    @Test
    void rotatingTheStampLeavesThePasswordAlone() {
        // Arrange
        UserAccount account = users.insert(CredentialsFixtures.userWithPassword());

        // Act
        users.rotateSecurityStamp(account.id(), "rotated", LATER);
        flushAndClear();

        // Assert
        UserAccount reloaded = users.findById(account.id()).orElseThrow();
        assertThat(reloaded.securityStamp()).isEqualTo("rotated");
        assertThat(reloaded.passwordHash()).isEqualTo("{argon2id}hash");
    }

    @Test
    void aHashIsUpgradedOnlyIfItIsStillTheOneThatWasVerified() {
        // Arrange
        UserAccount account = users.insert(CredentialsFixtures.userWithPassword());

        // Act
        boolean upgraded = users.replacePasswordHash(account.id(), "{argon2id}hash", "{argon2id}stronger", LATER);
        boolean stale = users.replacePasswordHash(account.id(), "{argon2id}hash", "{argon2id}overwrite", LATER);
        flushAndClear();

        // Assert
        assertThat(upgraded).isTrue();
        assertThat(stale).isFalse();
        assertThat(users.findById(account.id()).orElseThrow().passwordHash()).isEqualTo("{argon2id}stronger");
    }

    @Test
    void deletingAnAccountRemovesIt() {
        // Arrange
        UserAccount account = users.insert(CredentialsFixtures.user());
        flushAndClear();

        // Act
        users.deleteById(account.id());
        flushAndClear();

        // Assert
        assertThat(users.findById(account.id())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from app_user where id = ?", Integer.class, account.id())).isZero();
    }

    @Test
    void theDatabaseRefusesAnEmailThatIsNotNormalised() {
        // Arrange
        Executable act = () -> jdbc.update("insert into app_user (id, email, status, security_stamp, created_at, updated_at) "
                + "values (?, 'Mixed@Example.com', 'ACTIVE', 's', now(), now())", UUID.randomUUID());

        // Act
        DataIntegrityViolationException ex = assertThrows(DataIntegrityViolationException.class, act);

        // Assert
        assertThat(ex.getMessage()).contains("ck_app_user_email_normalised");
    }
}
