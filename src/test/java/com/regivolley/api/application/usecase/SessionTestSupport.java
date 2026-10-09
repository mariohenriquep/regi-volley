package com.regivolley.api.application.usecase;

import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.identity.RefreshToken;
import com.regivolley.api.application.identity.UserAccount;
import com.regivolley.api.application.result.SessionTokens;

import static org.mockito.Mockito.spy;

/** Shared wiring of the session tests: a harness, a spy over the refresh-token store, and the mechanics to open a session directly. */
final class SessionTestSupport {

    final CredentialsHarness h;
    final InMemoryCredentialStores.RefreshTokens tokens;
    final SessionIssuer sessions;
    final RefreshSessionService refreshSession;
    final UserAccount user;
    final Membership membership;

    /** A fresh harness with a confirmed user. */
    SessionTestSupport() {
        this(new CredentialsHarness());
    }

    /** Over an existing harness (the user is whoever the harness already holds, or a new confirmed one). */
    SessionTestSupport(CredentialsHarness harness) {
        this.h = harness;
        this.tokens = spy(h.stores.refreshTokens);
        this.sessions = new SessionIssuer(tokens, h.stores.users, h.stores.memberships, h.verifier, h.accessTokens, h.secrets, h.clock);
        this.refreshSession = new RefreshSessionService(tokens, h.stores.users, h.stores.memberships, h.verifier,
                h.accessTokens, h.secrets, h.transactions, h.clock);
        this.user = h.stores.users.byId.values().stream().findFirst().orElseGet(h::confirmedUser);
        this.membership = h.membershipOf(user);
    }

    SessionTokens open() {
        return sessions.open(user, membership);
    }

    SessionTokens open(UserAccount account) {
        return sessions.open(account, h.membershipOf(account));
    }

    SessionTokens refresh(String raw) {
        return refreshSession.execute(new com.regivolley.api.application.command.RefreshSessionCommand(raw));
    }

    RefreshToken stored(String raw) {
        return tokens.findByTokenHash(h.secrets.hash(raw)).orElseThrow();
    }
}
