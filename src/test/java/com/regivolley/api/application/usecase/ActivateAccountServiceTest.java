package com.regivolley.api.application.usecase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.regivolley.api.application.exception.InvalidLinkException;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLinkPolicy;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.domain.exception.InvalidFieldException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import static com.regivolley.api.application.usecase.LinkTestSupport.NEW_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/** Threat model D-11 and D-8: a password is set only by consuming a valid single-use link, and doing so ends every session. */
class ActivateAccountServiceTest {

    private final LinkTestSupport t = new LinkTestSupport();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(CredentialLinkConsumer.class);

    @BeforeEach
    void attach() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logs);
    }

    private Executable activating(String token, String password) {
        return () -> t.activate(token, password);
    }

    private String logText() {
        return String.join("\n", logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test
    void activatingConfirmsTheMembershipSetsTheHashedPasswordAndRotatesTheStamp() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        String oldStamp = t.user.securityStamp();

        // Act
        t.activate(link.token(), NEW_PASSWORD);

        // Assert
        var after = t.h.stores.users.byId.get(t.user.id());
        assertThat(t.h.membershipOf(t.user).status()).isEqualTo(MembershipStatus.CONFIRMED);
        assertThat(t.h.membershipOf(t.user).confirmedAt()).isEqualTo(t.h.clock.instant());
        assertThat(after.passwordHash()).isEqualTo(t.h.hasher.hash(NEW_PASSWORD)).isNotEqualTo(NEW_PASSWORD);
        assertThat(after.securityStamp()).isNotEqualTo(oldStamp);
        var stored = t.h.stores.links.findByTokenHash(t.h.secrets.hash(link.token())).orElseThrow();
        assertThat(stored.consumedAt()).isEqualTo(t.h.clock.instant());
    }

    @Test
    void aLinkWorksOnlyOnce() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        t.activate(link.token(), NEW_PASSWORD);
        Executable again = activating(link.token(), "another new passphrase");

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, again);

        // Assert - the first password stands
        assertThat(ex.getMessage()).isEqualTo("Invalid or expired link");
        assertThat(t.h.stores.users.byId.get(t.user.id()).passwordHash()).isEqualTo(t.h.hasher.hash(NEW_PASSWORD));
    }

    @Test
    void anActivationLinkExpiresAfterSevenDays() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        t.h.clock.advance(EmailLinkPolicy.ACTIVATION_LIFETIME.plusSeconds(1));
        Executable act = activating(link.token(), NEW_PASSWORD);

        // Act
        assertThrows(InvalidLinkException.class, act);

        // Assert
        assertThat(t.h.membershipOf(t.user).status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(t.h.stores.users.byId.get(t.user.id()).hasPassword()).isFalse();
    }

    @Test
    void anActivationLinkStillWorksJustBeforeSevenDays() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        t.h.clock.advance(EmailLinkPolicy.ACTIVATION_LIFETIME.minusSeconds(1));

        // Act
        t.activate(link.token(), NEW_PASSWORD);

        // Assert
        assertThat(t.h.membershipOf(t.user).status()).isEqualTo(MembershipStatus.CONFIRMED);
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"   ", "not-a-real-token"})
    void anUnknownOrBlankTokenIsRefusedWithoutSayingWhy(String token) {
        // Arrange
        t.activationLinkForPendingUser();
        Executable act = activating(token, NEW_PASSWORD);

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        assertThat(t.h.stores.users.byId.get(t.user.id()).hasPassword()).isFalse();
    }

    @Test
    void aResetTokenCannotActivate() {
        // Arrange
        AccountLink reset = t.resetLinkForActiveUser();
        Executable act = activating(reset.token(), NEW_PASSWORD);

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void aWeakPasswordIsRefusedAndDoesNotSpendTheLink() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        Executable weak = activating(link.token(), "password123");

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, weak);

        // Assert - the person can try again with the same link
        assertThat(ex.field()).isEqualTo("password");
        t.activate(link.token(), NEW_PASSWORD);
        assertThat(t.h.membershipOf(t.user).status()).isEqualTo(MembershipStatus.CONFIRMED);
    }

    @Test
    void aPasswordEqualToTheAssociationNameIsRefused() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        Executable act = activating(link.token(), t.h.association.name());

        // Act
        InvalidFieldException ex = assertThrows(InvalidFieldException.class, act);

        // Assert
        assertThat(ex.field()).isEqualTo("password");
    }

    @Test
    void aNewerLinkInvalidatesTheOlderOne() {
        // Arrange
        AccountLink old = t.activationLinkForPendingUser();
        AccountLink newer = t.issuer.issue(t.user, t.h.membershipOf(t.user), AccountLinkPurpose.ACTIVATION);
        Executable oldOne = activating(old.token(), NEW_PASSWORD);

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, oldOne);

        // Assert
        assertThat(ex).isNotNull();
        t.activate(newer.token(), NEW_PASSWORD);
        assertThat(t.h.membershipOf(t.user).status()).isEqualTo(MembershipStatus.CONFIRMED);
    }

    @Test
    void aDisabledAccountCannotUseItsLink() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        t.h.stores.users.disable(t.user.id());
        Executable act = activating(link.token(), NEW_PASSWORD);

        // Act
        InvalidLinkException ex = assertThrows(InvalidLinkException.class, act);

        // Assert
        assertThat(ex).isNotNull();
    }

    @Test
    void losingTheRaceToConsumeTheLinkIsRefusedAndChangesNothing() {
        // Arrange - another request consumed it between our check and our update
        AccountLink link = t.activationLinkForPendingUser();
        doReturn(false).when(t.links).consume(any(), any());
        Executable act = activating(link.token(), NEW_PASSWORD);

        // Act
        assertThrows(InvalidLinkException.class, act);

        // Assert
        assertThat(t.h.stores.users.byId.get(t.user.id()).hasPassword()).isFalse();
        assertThat(t.h.membershipOf(t.user).status()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void activatingRevokesTheUsersExistingSessions() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();
        var token = t.h.stores.refreshTokens.insert(com.regivolley.api.application.identity.RefreshToken.first(t.user.id(),
                t.h.membershipOf(t.user).id(), "some-hash", t.h.clock.instant()));

        // Act
        t.activate(link.token(), NEW_PASSWORD);

        // Assert
        assertThat(t.h.stores.refreshTokens.byId.get(token.id()).isRevoked()).isTrue();
    }

    @Test
    void theAuditLineCarriesIdsAndNeverTheTokenOrThePassword() {
        // Arrange
        AccountLink link = t.activationLinkForPendingUser();

        // Act
        t.activate(link.token(), NEW_PASSWORD);

        // Assert
        assertThat(logText()).contains("ACTIVATION").contains(t.user.id().toString())
                .doesNotContain(link.token()).doesNotContain(NEW_PASSWORD).doesNotContain(CredentialsHarness.EMAIL);
    }
}
