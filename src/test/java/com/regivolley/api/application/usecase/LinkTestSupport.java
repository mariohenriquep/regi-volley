package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.AccountLink;
import com.regivolley.api.application.identity.AccountLinkPurpose;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.port.CommonPasswordList;

import java.util.Set;

import static org.mockito.Mockito.spy;

/** Shared wiring of the link tests: harness, a spy over the link store, a tiny common-password list and the two consuming services. */
final class LinkTestSupport {

    static final String NEW_PASSWORD = "a brand new passphrase";

    final CredentialsHarness h = new CredentialsHarness();
    final InMemoryCredentialStores.Links links = spy(h.stores.links);
    final CommonPasswordList common = password -> Set.of("password123").contains(password.toLowerCase());
    final EmailLinkIssuer issuer = new EmailLinkIssuer(links, h.secrets, h.clock);
    final ActivateAccountService activate = new ActivateAccountService(links, h.stores.users, h.stores.memberships, h.associations, common,
            h.hasher, h.stores.refreshTokens, h.secrets, h.transactions, h.clock);
    final ResetPasswordService reset = new ResetPasswordService(links, h.stores.users, h.stores.memberships, h.associations, common,
            h.hasher, h.stores.refreshTokens, h.secrets, h.transactions, h.clock);
    UserAccount user;

    AccountLink activationLinkForPendingUser() {
        user = h.pendingUser();
        return issuer.issue(user, h.membershipOf(user), AccountLinkPurpose.ACTIVATION);
    }

    AccountLink resetLinkForActiveUser() {
        user = h.confirmedUser();
        return issuer.issue(user, null, AccountLinkPurpose.PASSWORD_RESET);
    }

    void activate(String token, String password) {
        activate.execute(new com.regivolley.api.application.command.ActivateAccountCommand(token, password));
    }

    void reset(String token, String password) {
        reset.execute(new com.regivolley.api.application.command.ResetPasswordCommand(token, password));
    }
}
