package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RequestPasswordResetCommand;
import com.regivolley.api.application.exception.LinkAlreadyIssuedException;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.EmailLinkPolicy;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.application.port.BackgroundWork;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Threat model D-10, D-11a: always the same answer, the mail work happens off the request, unknown emails are not distinguishable. */
class RequestPasswordResetServiceTest {

    private CredentialsHarness h;
    private AccountLinkMailer mailer;
    private AttemptThrottle throttle;
    private InMemoryCredentialStores.Links links;
    private RequestPasswordResetService service;
    private final List<Runnable> queued = new ArrayList<>();
    private final List<BackgroundWork.Lane> lanes = new ArrayList<>();
    private final BackgroundWork background = (lane, work) -> {
        lanes.add(lane);
        queued.add(work);
    };

    @BeforeEach
    void setUp() {
        h = new CredentialsHarness();
        mailer = mock(AccountLinkMailer.class);
        throttle = mock(AttemptThrottle.class);
        links = spy(h.stores.links);
        service = new RequestPasswordResetService(h.stores.users, h.stores.memberships, links, h.secrets, mailer, throttle, background,
                h.transactions, h.clock);
    }

    private void request(String email) {
        service.execute(new RequestPasswordResetCommand(email));
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
        queued.clear();
    }

    @Test
    void nothingHappensOnTheRequestThreadSoTheTimingIsTheSameForEveryEmail() {
        // Arrange
        h.confirmedUser();

        // Act
        request(CredentialsHarness.EMAIL);

        // Assert
        assertThat(queued).hasSize(1);
        assertThat(h.stores.links.byId).isEmpty();
        verifyNoInteractions(mailer);
    }

    @Test
    void theWorkUsesTheAccountMailLaneThatAdministratorResendsCannotFill() {
        // Arrange
        h.confirmedUser();

        // Act
        request(CredentialsHarness.EMAIL);

        // Assert
        assertThat(lanes).containsExactly(BackgroundWork.Lane.ACCOUNT_MAIL);
    }

    @Test
    void theThrottleIsTakenOnTheRequestThreadFirst() {
        // Arrange
        h.confirmedUser();

        // Act
        request(CredentialsHarness.EMAIL);

        // Assert
        verify(throttle).checkPasswordResetRequest(CredentialsHarness.EMAIL);
    }

    @Test
    void aThrottledRequestQueuesNothing() {
        // Arrange
        doThrow(new RateLimitExceededException(Duration.ofMinutes(20))).when(throttle).checkPasswordResetRequest(any());
        Executable act = () -> request("nobody@example.com");

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isEqualTo(1200);
        assertThat(queued).isEmpty();
    }

    @Test
    void anActivatedAccountGetsAResetLinkMailedToItsOwnAddress() {
        // Arrange
        UserAccount user = h.confirmedUser();
        request(CredentialsHarness.EMAIL);

        // Act
        runQueued();

        // Assert
        ArgumentCaptor<AccountLink> link = ArgumentCaptor.forClass(AccountLink.class);
        verify(mailer).send(eq(user.email()), eq(AccountLinkPurpose.PASSWORD_RESET), link.capture());
        EmailLink stored = h.stores.links.findByTokenHash(h.secrets.hash(link.getValue().token())).orElseThrow();
        assertThat(stored.purpose()).isEqualTo(AccountLinkPurpose.PASSWORD_RESET);
        assertThat(stored.userId()).isEqualTo(user.id());
        assertThat(stored.membershipId()).isNull();
        assertThat(stored.expiresAt()).isEqualTo(CredentialsHarness.NOW.plus(EmailLinkPolicy.RESET_LIFETIME));
    }

    @Test
    void theLinkGoesToTheAccountEmailNotToTheMembersContactAddress() {
        // Arrange
        h.confirmedUser();
        request(CredentialsHarness.EMAIL);

        // Act
        runQueued();

        // Assert
        ArgumentCaptor<EmailAddress> address = ArgumentCaptor.forClass(EmailAddress.class);
        verify(mailer).send(address.capture(), any(), any());
        assertThat(address.getValue()).isEqualTo(EmailAddress.of(CredentialsHarness.EMAIL)).isNotEqualTo(h.member.email());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nobody@example.com", "not-an-email"})
    void anUnknownOrMalformedEmailGetsNothingAndNoErrorAnywhere(String email) {
        // Arrange
        h.confirmedUser();
        request(email);

        // Act
        runQueued();

        // Assert
        verifyNoInteractions(mailer);
        assertThat(h.stores.links.byId).isEmpty();
    }

    @Test
    void anAccountThatWasNeverActivatedGetsItsActivationLinkAgain() {
        // Arrange
        UserAccount user = h.pendingUser();
        request(CredentialsHarness.EMAIL);

        // Act
        runQueued();

        // Assert
        ArgumentCaptor<AccountLink> link = ArgumentCaptor.forClass(AccountLink.class);
        verify(mailer).send(eq(user.email()), eq(AccountLinkPurpose.ACTIVATION), link.capture());
        EmailLink stored = h.stores.links.findByTokenHash(h.secrets.hash(link.getValue().token())).orElseThrow();
        assertThat(stored.membershipId()).isEqualTo(h.membershipOf(user).id());
    }

    @Test
    void aDisabledAccountGetsNothing() {
        // Arrange
        UserAccount user = h.confirmedUser();
        h.stores.users.disable(user.id());
        request(CredentialsHarness.EMAIL);

        // Act
        runQueued();

        // Assert
        verifyNoInteractions(mailer);
    }

    @Test
    void anAccountWithoutAnyMembershipGetsNothing() {
        // Arrange
        UserAccount user = h.confirmedUser();
        h.stores.memberships.byId.clear();
        request(CredentialsHarness.EMAIL);

        // Act
        runQueued();

        // Assert
        verifyNoInteractions(mailer);
        assertThat(h.stores.users.byId).containsKey(user.id());
    }

    @Test
    void aNewerResetLinkSupersedesTheOlderOne() {
        // Arrange
        h.confirmedUser();
        request(CredentialsHarness.EMAIL);
        runQueued();
        request(CredentialsHarness.EMAIL);

        // Act
        runQueued();

        // Assert
        assertThat(h.stores.links.byId.values()).hasSize(1).allMatch(link -> link.consumedAt() == null);
    }

    @Test
    void losingTheRaceToIssueTheLinkIsRepeated() {
        // Arrange
        h.confirmedUser();
        org.mockito.Mockito.doAnswer(invocation -> {
            throw new LinkAlreadyIssuedException();
        }).doCallRealMethod().when(links).insert(any());
        request(CredentialsHarness.EMAIL);

        // Act
        runQueued();

        // Assert
        verify(mailer).send(any(), any(), any());
    }

    @Test
    void aFailureInTheBackgroundWorkIsContained() {
        // Arrange
        h.confirmedUser();
        doThrow(new IllegalStateException("mail provider rejected ana.silva@example.com")).when(mailer).send(any(), any(), any());
        request(CredentialsHarness.EMAIL);
        Runnable work = queued.get(0);

        // Act
        work.run();

        // Assert - it tried, failed, and nothing escaped
        verify(mailer).send(any(), any(), any());
    }
}
