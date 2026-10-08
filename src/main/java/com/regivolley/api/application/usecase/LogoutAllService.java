package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LogoutAllCommand;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.port.UserAccountStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Ends every login of the account and changes its security stamp, so access tokens issued so far stop working on their next
 * request instead of at their 10-minute expiry (threat model D-4, U3).
 */
@Service
public class LogoutAllService implements LogoutAllUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LogoutAllService.class);

    private final RefreshTokenStore tokens;
    private final UserAccountStore users;
    private final SecretGenerator secrets;
    private final TransactionRunner transactions;
    private final Clock clock;

    public LogoutAllService(RefreshTokenStore tokens, UserAccountStore users, SecretGenerator secrets, TransactionRunner transactions,
                            Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.secrets = secrets;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Void execute(LogoutAllCommand command) {
        transactions.inNewTransaction(() -> {
            Instant now = clock.instant();
            int revoked = tokens.revokeAllOf(command.userId(), now);
            users.rotateSecurityStamp(command.userId(), secrets.newSecret(), now);
            LOG.info("Logout of all sessions: userId={} revokedTokens={}", command.userId(), revoked);
            return null;
        });
        return null;
    }
}
