package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ResendActivationLinkCommand;
import com.regivolley.api.application.exception.LinkAlreadyIssuedException;
import com.regivolley.api.application.exception.RateLimitExceededException;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.EmailLinkPolicy;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.application.port.AccountProvisioner;
import com.regivolley.api.application.port.AttemptThrottle;
import com.regivolley.api.application.port.BackgroundWork;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.MemberId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Threat model section 6 and P7: an administrator re-sends the activation link of a member who has not activated. The answer
 * never depends on what was found, the work happens off the request thread, the link supersedes the old one and goes to the
 * account's own address.
 */
class ResendActivationLinkServiceTest {

    private CredentialsHarness h;
    private AccountLinkMailer mailer;
    private AccountProvisioner provisioner;
    private AttemptThrottle throttle;
    private InMemoryCredentialStores.Links links;
    private Member admin;
    private ResendActivationLinkService service;
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
        provisioner = mock(AccountProvisioner.class);
        throttle = mock(AttemptThrottle.class);
        links = spy(h.stores.links);
        admin = Data.admin(h.association);
        knows(admin);
        service = new ResendActivationLinkService(h.members, h.stores.users, h.stores.memberships, links, h.secrets, mailer, provisioner,
                throttle, background, h.transactions, h.clock);
    }

    private void knows(Member person) {
        when(h.members.findById(h.association.id(), person.id())).thenReturn(Optional.of(person));
    }

    private ResendActivationLinkCommand resend(Member actor, MemberId target) {
        return new ResendActivationLinkCommand(Data.actor(actor), target);
    }

    private void resendToMember() {
        service.execute(resend(admin, h.member.id()));
    }

    private void runQueued() {
        queued.forEach(Runnable::run);
        queued.clear();
    }

    @Test
    void nothingIsReadOrWrittenOnTheRequestThreadBeyondTheChecks() {
        // Arrange
        h.pendingUser();

        // Act
        resendToMember();

        // Assert
        assertThat(queued).hasSize(1);
        assertThat(h.stores.links.byId).isEmpty();
        verifyNoInteractions(mailer, provisioner);
    }

    @Test
    void aPendingMembersLinkIsMailedToTheAccountAddressNotTheMembersContactAddress() {
        // Arrange
        UserAccount user = h.pendingUser();
        resendToMember();

        // Act
        runQueued();

        // Assert
        ArgumentCaptor<AccountLink> link = ArgumentCaptor.forClass(AccountLink.class);
        ArgumentCaptor<EmailAddress> address = ArgumentCaptor.forClass(EmailAddress.class);
        verify(mailer).send(address.capture(), eq(AccountLinkPurpose.ACTIVATION), link.capture());
        assertThat(address.getValue()).isEqualTo(user.email()).isNotEqualTo(h.member.email());
        EmailLink stored = h.stores.links.findByTokenHash(h.secrets.hash(link.getValue().token())).orElseThrow();
        assertThat(stored.purpose()).isEqualTo(AccountLinkPurpose.ACTIVATION);
        assertThat(stored.userId()).isEqualTo(user.id());
        assertThat(stored.membershipId()).isEqualTo(h.membershipOf(user).id());
        assertThat(stored.expiresAt()).isEqualTo(CredentialsHarness.NOW.plus(EmailLinkPolicy.ACTIVATION_LIFETIME));
        assertThat(stored.consumedAt()).isNull();
        verifyNoInteractions(provisioner);
    }

    @Test
    void theNewLinkSupersedesTheOldOne() {
        // Arrange
        h.pendingUser();
        resendToMember();
        runQueued();
        EmailLink first = h.stores.links.byId.values().iterator().next();
        h.clock.advance(Duration.ofMinutes(5));
        resendToMember();

        // Act
        runQueued();

        // Assert
        // the superseded link is spent and cleared away on the way, so only the new one is left, and it is open
        assertThat(h.stores.links.byId).doesNotContainKey(first.id());
        assertThat(h.stores.links.byId.values()).hasSize(1).allMatch(link -> link.consumedAt() == null);
    }

    @Test
    void aMemberWhoAlreadyActivatedGetsNothing() {
        // Arrange
        h.confirmedUser();
        resendToMember();

        // Act
        runQueued();

        // Assert
        verifyNoInteractions(mailer, provisioner);
        assertThat(h.stores.links.byId).isEmpty();
    }

    @Test
    void aDisabledAccountGetsNothing() {
        // Arrange
        UserAccount user = h.pendingUser();
        h.stores.users.disable(user.id());
        resendToMember();

        // Act
        runQueued();

        // Assert
        verifyNoInteractions(mailer, provisioner);
        assertThat(h.stores.links.byId).isEmpty();
    }

    @Test
    void aMemberWithNoMembershipYetIsProvisionedAgainWithTheirContactAddress() {
        // Arrange - the provisioning after the member was committed failed (P7): there is no account to re-issue a link for
        resendToMember();

        // Act
        runQueued();

        // Assert
        verify(provisioner).provision(h.association.id(), h.member.id(), h.member.email());
        verifyNoInteractions(mailer);
    }

    @Test
    void aDeactivatedMemberIsNeverQueuedSoGetsNoLinkAndNoAccount() {
        // Arrange
        Member inactive = h.member.deactivate();
        when(h.members.findById(h.association.id(), inactive.id())).thenReturn(Optional.of(inactive));
        h.pendingUser();

        // Act
        service.execute(resend(admin, inactive.id()));

        // Assert
        assertThat(queued).isEmpty();
        verifyNoInteractions(mailer, provisioner);
    }

    @Test
    void anAnonymisedMemberIsNeverQueued() {
        // Arrange
        Member erased = h.member.anonymise(h.clock);
        when(h.members.findById(h.association.id(), erased.id())).thenReturn(Optional.of(erased));

        // Act
        service.execute(resend(admin, erased.id()));

        // Assert
        assertThat(queued).isEmpty();
        verifyNoInteractions(mailer, provisioner);
    }

    @Test
    void aCoachIsRefusedBeforeTheThrottleIsTaken() {
        // Arrange
        Member coach = Data.coach(h.association);
        knows(coach);
        Executable act = () -> service.execute(resend(coach, h.member.id()));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        verifyNoInteractions(throttle);
        assertThat(queued).isEmpty();
    }

    @Test
    void aDeactivatedAdministratorIsRefused() {
        // Arrange
        Member inactive = admin.deactivate();
        knows(inactive);
        Executable act = () -> service.execute(resend(inactive, h.member.id()));

        // Act
        assertThrows(NotAllowedException.class, act);

        // Assert
        assertThat(queued).isEmpty();
    }

    @Test
    void aMemberOfAnotherAssociationIsNotFoundAndNothingIsQueued() {
        // Arrange
        MemberId foreign = MemberId.generate();
        when(h.members.findById(h.association.id(), foreign)).thenReturn(Optional.empty());
        Executable act = () -> service.execute(resend(admin, foreign));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        assertThat(queued).isEmpty();
        verifyNoInteractions(mailer, provisioner);
    }

    @Test
    void theThrottleIsTakenOnTheRequestThreadForTheTargetInTheActorsAssociation() {
        // Arrange
        h.pendingUser();

        // Act
        resendToMember();

        // Assert
        verify(throttle).checkActivationLinkResend(h.association.id(), h.member.id());
    }

    @Test
    void aThrottledRequestQueuesNothing() {
        // Arrange
        doThrow(new RateLimitExceededException(Duration.ofMinutes(20))).when(throttle).checkActivationLinkResend(any(), any());
        Executable act = () -> resendToMember();

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isEqualTo(1200);
        assertThat(queued).isEmpty();
    }

    @Test
    void losingTheRaceToIssueTheLinkIsRepeated() {
        // Arrange
        h.pendingUser();
        org.mockito.Mockito.doAnswer(invocation -> {
            throw new LinkAlreadyIssuedException();
        }).doCallRealMethod().when(links).insert(any());
        resendToMember();

        // Act
        runQueued();

        // Assert
        verify(mailer).send(any(), eq(AccountLinkPurpose.ACTIVATION), any());
    }

    @Test
    void aFailureInTheBackgroundWorkIsContained() {
        // Arrange
        h.pendingUser();
        doThrow(new IllegalStateException("mail provider rejected ana.silva@example.com")).when(mailer).send(any(), any(), any());
        resendToMember();
        Runnable work = queued.get(0);

        // Act
        work.run();

        // Assert - it tried, failed, and nothing escaped
        verify(mailer).send(any(), any(), any());
    }

    @Test
    void aProvisioningFailureIsContained() {
        // Arrange
        doThrow(new IllegalStateException("could not insert ana.silva@example.com")).when(provisioner).provision(any(), any(), any());
        resendToMember();
        Runnable work = queued.get(0);

        // Act
        work.run();

        // Assert
        verify(provisioner).provision(any(), any(), any());
    }

    @Test
    void theWorkUsesTheAdminResendLaneSoItCannotTakeThePasswordResetCapacity() {
        // Arrange
        h.pendingUser();

        // Act
        resendToMember();

        // Assert
        assertThat(lanes).containsExactly(BackgroundWork.Lane.ADMIN_RESEND);
    }

    @Test
    void aFullQueueDropsTheWorkAndTheCallerStillGetsTheSameAnswer() {
        // Arrange - an adapter whose lane is full drops the work (and logs it) without telling the caller
        List<Runnable> dropped = new ArrayList<>();
        ResendActivationLinkService saturated = new ResendActivationLinkService(h.members, h.stores.users, h.stores.memberships, links,
                h.secrets, mailer, provisioner, throttle, (lane, work) -> dropped.add(work), h.transactions, h.clock);
        h.pendingUser();

        // Act
        Void answer = saturated.execute(resend(admin, h.member.id()));

        // Assert
        assertThat(answer).isNull();
        verifyNoInteractions(mailer, provisioner);
    }

    @Test
    void anIdOfAnotherAssociationSpendsTheSameBudgetAsOneThatExistsAndTheAddressIsNeverCounted() {
        // Arrange
        MemberId foreign = MemberId.generate();
        when(h.members.findById(h.association.id(), foreign)).thenReturn(Optional.empty());
        Executable act = () -> service.execute(resend(admin, foreign));

        // Act
        assertThrows(MemberNotFoundException.class, act);

        // Assert
        verify(throttle).checkActivationLinkResend(h.association.id(), foreign);
        verify(throttle, org.mockito.Mockito.never()).checkLinkMailAddress(any());
    }

    @Test
    void theMailThrottleIsTakenForTheMembersAddressOnTheRequestThread() {
        // Arrange
        h.pendingUser();

        // Act
        resendToMember();

        // Assert
        verify(throttle).checkLinkMailAddress(h.member.email());
    }

    @Test
    void aMailboxMailedTooOftenQueuesNothing() {
        // Arrange
        doThrow(new RateLimitExceededException(Duration.ofMinutes(40))).when(throttle).checkLinkMailAddress(any());
        h.pendingUser();
        Executable act = () -> resendToMember();

        // Act
        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, act);

        // Assert
        assertThat(ex.retryAfterSeconds()).isEqualTo(2400);
        assertThat(queued).isEmpty();
        verifyNoInteractions(mailer, provisioner);
    }

    @Test
    void aMemberDeactivatedAfterTheRequestButBeforeTheWorkRunsGetsNothing() {
        // Arrange
        h.pendingUser();
        resendToMember();
        Member inactive = h.member.deactivate();
        when(h.members.findById(h.association.id(), inactive.id())).thenReturn(Optional.of(inactive));

        // Act
        runQueued();

        // Assert
        verifyNoInteractions(mailer, provisioner);
        assertThat(h.stores.links.byId).isEmpty();
    }

    @Test
    void aMemberErasedAfterTheRequestButBeforeTheWorkRunsIsNotProvisioned() {
        // Arrange - no membership either: without the re-read this would provision the placeholder address
        resendToMember();
        Member erased = h.member.anonymise(h.clock);
        when(h.members.findById(h.association.id(), erased.id())).thenReturn(Optional.of(erased));

        // Act
        runQueued();

        // Assert
        verifyNoInteractions(mailer, provisioner);
    }

    @Test
    void provisioningUsesTheContactAddressTheMemberHasWhenTheWorkRuns() {
        // Arrange
        resendToMember();

        // Act
        runQueued();

        // Assert
        ArgumentCaptor<EmailAddress> address = ArgumentCaptor.forClass(EmailAddress.class);
        verify(provisioner).provision(eq(h.association.id()), eq(h.member.id()), address.capture());
        assertThat(address.getValue()).isEqualTo(h.member.email());
    }
}
