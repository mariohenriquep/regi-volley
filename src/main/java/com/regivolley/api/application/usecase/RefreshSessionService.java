package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RefreshSessionCommand;
import com.regivolley.api.application.exception.InvalidRefreshTokenException;
import com.regivolley.api.application.port.AccessTokenIssuer;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.port.PrincipalVerifier;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import com.regivolley.api.application.result.SessionTokens;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Rotates the presented refresh token (threat model D-7): the rules - rotation on every use, reuse detection with a grace window,
 * idle and absolute lifetimes, re-checking that the member may still sign in - are in {@link SessionIssuer}. The work runs in one
 * transaction that returns an outcome instead of throwing, so a family revoked because of reuse stays revoked although the caller
 * then gets {@link InvalidRefreshTokenException}.
 */
@Service
public class RefreshSessionService implements RefreshSessionUseCase {

    private final SessionIssuer sessions;
    private final TransactionRunner transactions;

    public RefreshSessionService(RefreshTokenStore refreshTokens, UserAccountStore users, MembershipStore memberships,
                                 PrincipalVerifier verifier, AccessTokenIssuer accessTokens, SecretGenerator secrets,
                                 TransactionRunner transactions, Clock clock) {
        this.sessions = new SessionIssuer(refreshTokens, users, memberships, verifier, accessTokens, secrets, clock);
        this.transactions = transactions;
    }

    /** @throws InvalidRefreshTokenException for every reason the token cannot be used, without saying which */
    @Override
    public SessionTokens execute(RefreshSessionCommand command) {
        String token = command.refreshToken();
        if (token == null || token.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        return transactions.inNewTransaction(() -> sessions.rotate(token)).orElseThrow(InvalidRefreshTokenException::new);
    }
}
