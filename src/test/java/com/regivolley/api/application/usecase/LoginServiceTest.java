package com.regivolley.api.application.usecase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.regivolley.api.application.command.LoginCommand;
import com.regivolley.api.application.exception.InvalidCredentialsException;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.CredentialAttemptThrottle;
import com.regivolley.api.application.port.PasswordHasher;
import com.regivolley.api.application.result.SessionTokens;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Threat model D-10: one failure for every reason, a hash verification even for unknown emails, throttling by email first. */
class LoginServiceTest {

    private static final String CLIENT_IP = "203.0.113.77";
    private static final String WRONG = "not the password at all";

    private CredentialsHarness h;
    private PasswordHasher hasher;
    private CredentialAttemptThrottle throttle;
    private LoginService login;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoginService.class);

    @BeforeEach
    void setUp() {
        h = new CredentialsHarness();
        hasher = spy(h.hasher);
        throttle = mock(CredentialAttemptThrottle.class);
        login = new LoginService(h.stores.users, h.stores.memberships, hasher, h.verifier, throttle, h.stores.refreshTokens,
                h.accessTokens, h.secrets, h.transactions, h.clock);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logs);
    }

    private LoginCommand command(String email, String password) {
        return new LoginCommand(email, password, CLIENT_IP);
    }

    private Executable loggingIn(String email, String password) {
        return () -> login.execute(command(email, password));
    }

    private String logText() {
        return String.join("\n", logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    @Test
    void logsInWithTheRightPasswordAndStartsASession() {
        // Arrange
        UserAccount user = h.confirmedUser();

        // Act
        SessionTokens tokens = login.execute(command(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD));

        // Assert
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.expiresInSeconds()).isEqualTo(600);
        assertThat(h.stores.refreshTokens.findByTokenHash(h.secrets.hash(tokens.refreshToken())))
                .hasValueSatisfying(stored -> assertThat(stored.userId()).isEqualTo(user.id()));
        assertThat(logText()).contains("Login succeeded").contains(user.id().toString()).doesNotContain(CredentialsHarness.EMAIL);
    }

    @Test
    void theEmailIsTrimmedAndCaseInsensitive() {
        // Arrange
        h.confirmedUser();

        // Act
        SessionTokens tokens = login.execute(command("  Ana.Silva@EXAMPLE.com ", CredentialsHarness.PASSWORD));

        // Assert
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    @Test
    void theThrottleIsTakenFirstOnTheAddressAsTyped() {
        // Arrange
        h.confirmedUser();

        // Act
        login.execute(command("  Ana.Silva@EXAMPLE.com ", CredentialsHarness.PASSWORD));

        // Assert
        verify(throttle).checkLogin("  Ana.Silva@EXAMPLE.com ");
    }

    @Test
    void aThrottledAttemptIsRefusedBeforeAnyLookupOrHashing() {
        // Arrange
        h.confirmedUser();
        doThrow(new RateLimitExceededException(Duration.ofMinutes(5))).when(throttle).checkLogin(anyString());
        Executable act = loggingIn(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD);

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert - even with the right password, and no hash was spent
        assertThat(ex.retryAfterSeconds()).isEqualTo(300);
        verify(hasher, never()).matches(anyString(), anyString());
        verify(hasher, never()).burn(anyString());
        assertThat(h.stores.refreshTokens.byId).isEmpty();
    }

    @Test
    void aWrongPasswordFails() {
        // Arrange
        h.confirmedUser();
        Executable act = loggingIn(CredentialsHarness.EMAIL, WRONG);

        // Act
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, act);

        // Assert
        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
        assertThat(h.stores.refreshTokens.byId).isEmpty();
    }

    @Test
    void anUnknownEmailStillVerifiesAHashSoTheTimingMatches() {
        // Arrange
        h.confirmedUser();
        Executable act = loggingIn("nobody@example.com", CredentialsHarness.PASSWORD);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        verify(hasher, times(1)).burn(CredentialsHarness.PASSWORD);
        verify(hasher, times(1)).matches(eq(CredentialsHarness.PASSWORD), anyString());
    }

    @Test
    void aKnownEmailVerifiesExactlyOneHashToo() {
        // Arrange
        h.confirmedUser();
        Executable act = loggingIn(CredentialsHarness.EMAIL, WRONG);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        verify(hasher, times(1)).matches(eq(WRONG), anyString());
        verify(hasher, never()).burn(anyString());
    }

    @Test
    void aMalformedEmailFailsLikeAnUnknownOne() {
        // Arrange
        Executable act = loggingIn("not-an-email", CredentialsHarness.PASSWORD);

        // Act
        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class, act);

        // Assert
        verify(hasher).burn(CredentialsHarness.PASSWORD);
        assertThat(ex.getMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void anAccountThatWasNeverActivatedFailsAndStillBurnsAHash() {
        // Arrange
        h.pendingUser();
        Executable act = loggingIn(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        verify(hasher).burn(CredentialsHarness.PASSWORD);
    }

    @Test
    void aPendingMembershipCannotLogInEvenWithTheRightPassword() {
        // Arrange
        UserAccount user = h.pendingUser();
        h.stores.users.setPassword(user.id(), h.hasher.hash(CredentialsHarness.PASSWORD), user.securityStamp(), CredentialsHarness.NOW);
        Executable act = loggingIn(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        assertThat(h.stores.refreshTokens.byId).isEmpty();
        assertThat(logText()).contains("NO_CONFIRMED_MEMBERSHIP");
    }

    @Test
    void aDisabledAccountCannotLogIn() {
        // Arrange
        UserAccount user = h.confirmedUser();
        h.stores.users.disable(user.id());
        Executable act = loggingIn(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        assertThat(h.stores.refreshTokens.byId).isEmpty();
        assertThat(logText()).contains("ACCOUNT_DISABLED");
    }

    @Test
    void anInactiveMemberCannotLogIn() {
        // Arrange
        h.confirmedUser();
        Member inactive = h.member.deactivate();
        when(h.members.findById(h.association.id(), h.member.id())).thenReturn(Optional.of(inactive));
        Executable act = loggingIn(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        assertThat(h.stores.refreshTokens.byId).isEmpty();
        assertThat(logText()).contains("MEMBER_INACTIVE");
    }

    @Test
    void moreThanOneConfirmedMembershipFailsClosedUntilASelectionStepExists() {
        // Arrange
        UserAccount user = h.confirmedUser();
        var otherAssociation = Data.association();
        Member otherMember = Data.member(otherAssociation);
        h.stores.memberships.insert(new Membership(UUID.randomUUID(), user.id(), otherAssociation.id(), otherMember.id(),
                MembershipStatus.CONFIRMED, CredentialsHarness.NOW, CredentialsHarness.NOW));
        Executable act = loggingIn(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        assertThat(h.stores.refreshTokens.byId).isEmpty();
        assertThat(logText()).contains("MULTIPLE_MEMBERSHIPS").contains(user.id().toString());
    }

    @Test
    void theFailureIsAuditedWithTheReasonAndATruncatedAddressNeverTheEmailOrPassword() {
        // Arrange
        UserAccount user = h.confirmedUser();
        Executable act = loggingIn(CredentialsHarness.EMAIL, WRONG);

        // Act
        assertThrows(InvalidCredentialsException.class, act);

        // Assert
        String text = logText();
        assertThat(text).contains("Login failed").contains("WRONG_PASSWORD").contains(user.id().toString()).contains("203.0.113.0/24");
        assertThat(text).doesNotContain(CLIENT_IP).doesNotContain(CredentialsHarness.EMAIL).doesNotContain(WRONG);
    }

    @Test
    void aLegacyHashIsReplacedByTheCurrentFormatOnSuccess() {
        // Arrange
        UserAccount user = h.confirmedUser();
        h.stores.users.setPassword(user.id(), "{legacy}l:" + CredentialsHarness.PASSWORD, user.securityStamp(), CredentialsHarness.NOW);

        // Act
        login.execute(command(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD));

        // Assert
        assertThat(h.stores.users.byId.get(user.id()).passwordHash()).isEqualTo("{fast}f:" + CredentialsHarness.PASSWORD);
        assertThat(h.stores.users.byId.get(user.id()).securityStamp()).isEqualTo(user.securityStamp());
    }

    @Test
    void aCurrentHashIsLeftAlone() {
        // Arrange
        UserAccount user = h.confirmedUser();
        String before = h.stores.users.byId.get(user.id()).passwordHash();

        // Act
        login.execute(command(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD));

        // Assert
        assertThat(h.stores.users.byId.get(user.id()).passwordHash()).isEqualTo(before);
    }

    @Test
    void aMemberWithSeveralRolesLogsInTheSame() {
        // Arrange
        h.confirmedUser();
        h.member = h.member.grantRole(MemberRole.COACH);
        when(h.members.findById(h.association.id(), h.member.id())).thenReturn(Optional.of(h.member));

        // Act
        SessionTokens tokens = login.execute(command(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD));

        // Assert
        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void theSessionIsOpenedInOneTransaction() {
        // Arrange
        h.confirmedUser();

        // Act
        login.execute(command(CredentialsHarness.EMAIL, CredentialsHarness.PASSWORD));

        // Assert
        assertThat(h.transactions.opened()).isEqualTo(1);
    }
}
