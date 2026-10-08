package com.regivolley.api.application.usecase;

import com.regivolley.api.application.exception.AccountAlreadyExistsException;
import com.regivolley.api.application.exception.LinkAlreadyIssuedException;
import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.EmailLink;
import com.regivolley.api.application.identity.EmailLinkPolicy;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.MembershipStatus;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.identity.UserStatus;
import com.regivolley.api.application.port.AccountLinkMailer;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Threat model D-11 and section 6: an account and a PENDING membership are created, an activation link is mailed to the account after the commit. */
class AccountProvisionerServiceTest {

    private final EmailAddress email = EmailAddress.of(CredentialsHarness.EMAIL);

    private CredentialsHarness h;
    private AccountLinkMailer mailer;
    private AccountProvisionerService provisioner;
    private InMemoryCredentialStores.Users users;
    private InMemoryCredentialStores.Links links;
    private boolean transactionOpen;

    @BeforeEach
    void setUp() {
        h = new CredentialsHarness();
        users = spy(h.stores.users);
        links = spy(h.stores.links);
        mailer = mock(AccountLinkMailer.class);
        com.regivolley.api.application.port.TransactionRunner tracking = new com.regivolley.api.application.port.TransactionRunner() {
            @Override
            public <T> T inNewTransaction(java.util.function.Supplier<T> work) {
                transactionOpen = true;
                try {
                    return work.get();
                } finally {
                    transactionOpen = false;
                }
            }
        };
        provisioner = new AccountProvisionerService(users, h.stores.memberships, links, h.secrets, mailer, tracking, h.clock);
    }

    private AccountLink sentLink() {
        ArgumentCaptor<AccountLink> link = ArgumentCaptor.forClass(AccountLink.class);
        verify(mailer).send(any(), any(), link.capture());
        return link.getValue();
    }

    @Test
    void createsTheAccountWithoutAPasswordAPendingMembershipAndAnActivationLinkSentAfterTheCommit() {
        // Arrange
        doAnswer(invocation -> {
            assertThat(transactionOpen).as("the link is sent only after the commit").isFalse();
            return null;
        }).when(mailer).send(any(), any(), any());

        // Act
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Assert
        UserAccount user = h.stores.users.findByEmail(email).orElseThrow();
        assertThat(user.hasPassword()).isFalse();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.securityStamp()).hasSize(43);
        Membership membership = h.membershipOf(user);
        assertThat(membership.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.memberId()).isEqualTo(h.member.id());
        AccountLink link = sentLink();
        EmailLink stored = h.stores.links.findByTokenHash(h.secrets.hash(link.token())).orElseThrow();
        assertThat(stored.purpose()).isEqualTo(AccountLinkPurpose.ACTIVATION);
        assertThat(stored.membershipId()).isEqualTo(membership.id());
        assertThat(stored.expiresAt()).isEqualTo(CredentialsHarness.NOW.plus(EmailLinkPolicy.ACTIVATION_LIFETIME));
        assertThat(link.reference()).isEqualTo(stored.id());
    }

    @Test
    void theLinkIsMailedToTheAccountsAddressNotToAMemberContact() {
        // Arrange
        // (the member's own contact address differs from the account email on purpose)
        assertThat(h.member.email()).isNotEqualTo(email);

        // Act
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Assert
        ArgumentCaptor<EmailAddress> address = ArgumentCaptor.forClass(EmailAddress.class);
        verify(mailer).send(address.capture(), any(), any());
        assertThat(address.getValue()).isEqualTo(h.stores.users.findByEmail(email).orElseThrow().email()).isEqualTo(email);
    }

    @Test
    void theTokenIsNotStoredInClear() {
        // Arrange
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Act
        AccountLink link = sentLink();

        // Assert
        assertThat(h.stores.links.byId.values()).noneMatch(stored -> stored.tokenHash().equals(link.token()));
        assertThat(link.token()).hasSize(43);
    }

    @Test
    void askingTwiceCreatesNothingTwiceAndSupersedesTheFirstLink() {
        // Arrange
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Act
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Assert
        assertThat(h.stores.users.byId).hasSize(1);
        assertThat(h.stores.memberships.byId).hasSize(1);
        assertThat(h.stores.links.byId.values()).hasSize(1).allMatch(link -> link.consumedAt() == null);
        ArgumentCaptor<AccountLink> sent = ArgumentCaptor.forClass(AccountLink.class);
        verify(mailer, times(2)).send(any(), any(), sent.capture());
        assertThat(h.stores.links.findByTokenHash(h.secrets.hash(sent.getAllValues().get(0).token()))).isEmpty();
        assertThat(h.stores.links.findByTokenHash(h.secrets.hash(sent.getAllValues().get(1).token()))).isPresent();
    }

    @Test
    void anExistingAccountOfThatEmailOnlyGainsAPendingMembership() {
        // Arrange
        UserAccount existing = h.confirmedUser();
        h.stores.memberships.byId.clear();

        // Act
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Assert
        assertThat(h.stores.users.byId).hasSize(1);
        assertThat(h.stores.users.byId.get(existing.id()).passwordHash()).isEqualTo(existing.passwordHash());
        assertThat(h.membershipOf(existing).status()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    void aMembershipThatIsAlreadyConfirmedGetsNoNewLink() {
        // Arrange
        h.confirmedUser();

        // Act
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Assert
        verifyNoInteractions(mailer);
        assertThat(h.stores.links.byId).isEmpty();
    }

    @Test
    void aMemberThatBelongsToAnotherAccountIsRefusedBeforeAnythingIsCreated() {
        // Arrange
        UserAccount other = UserAccount.create(EmailAddress.of("someone.else@example.com"), h.secrets.newSecret(), CredentialsHarness.NOW);
        h.stores.users.insert(other);
        h.stores.memberships.insert(Membership.pending(other.id(), h.association.id(), h.member.id(), CredentialsHarness.NOW));
        Executable act = () -> provisioner.provision(h.association.id(), h.member.id(), email);

        // Act
        IllegalStateException ex = assertThrows(IllegalStateException.class, act);

        // Assert
        assertThat(ex.getMessage()).doesNotContain("@");
        verify(mailer, never()).send(any(), any(), any());
        assertThat(h.stores.users.byId).hasSize(1);
    }

    @Test
    void losingTheRaceToCreateTheAccountReadsTheWinnersAndCarriesOn() {
        // Arrange - another provisioning inserts the same email between our lookup and our insert
        UserAccount winner = UserAccount.create(email, h.secrets.newSecret(), CredentialsHarness.NOW);
        doAnswer(invocation -> {
            h.stores.users.byId.put(winner.id(), winner);
            throw new AccountAlreadyExistsException();
        }).doCallRealMethod().when(users).insert(any());

        // Act
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Assert
        assertThat(h.stores.users.byId).containsOnlyKeys(winner.id());
        assertThat(h.membershipOf(winner).status()).isEqualTo(MembershipStatus.PENDING);
        verify(mailer).send(any(), any(), any());
    }

    @Test
    void losingTheRaceToIssueTheLinkRepeatsTheTransactionAndTheNewestLinkWins() {
        // Arrange - a concurrent request issued a link for the same membership between our invalidate and our insert
        doAnswer(invocation -> {
            throw new LinkAlreadyIssuedException();
        }).doCallRealMethod().when(links).insert(any());

        // Act
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Assert
        assertThat(h.stores.links.byId.values()).hasSize(1);
        verify(mailer).send(any(), any(), any());
    }

    @Test
    void givesUpWhenTheRaceKeepsBeingLost() {
        // Arrange
        doAnswer(invocation -> {
            throw new LinkAlreadyIssuedException();
        }).when(links).insert(any());
        Executable act = () -> provisioner.provision(h.association.id(), h.member.id(), email);

        // Act
        LinkAlreadyIssuedException ex = assertThrows(LinkAlreadyIssuedException.class, act);

        // Assert
        assertThat(ex).isNotNull();
        verifyNoInteractions(mailer);
    }

    @Test
    void theLinkReferenceIsAUuidThatAppearsInTheStore() {
        // Arrange
        provisioner.provision(h.association.id(), h.member.id(), email);

        // Act
        UUID reference = sentLink().reference();

        // Assert
        assertThat(h.stores.links.byId).containsKey(reference);
    }
}
