package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LogoutCommand;
import com.regivolley.api.application.port.RefreshTokenStore;
import com.regivolley.api.application.port.SecretGenerator;
import com.regivolley.api.application.port.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Ends the login the presented refresh token belongs to. Unknown, missing or already ended tokens are a quiet no-op (threat model D-7). */
@Service
public class LogoutService implements LogoutUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(LogoutService.class);

    private final RefreshTokenStore tokens;
    private final SecretGenerator secrets;
    private final TransactionRunner transactions;
    private final Clock clock;

    public LogoutService(RefreshTokenStore tokens, SecretGenerator secrets, TransactionRunner transactions, Clock clock) {
        this.tokens = tokens;
        this.secrets = secrets;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public Void execute(LogoutCommand command) {
        String raw = command.refreshToken();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        transactions.inNewTransaction(() -> {
            tokens.findByTokenHash(secrets.hash(raw)).ifPresent(token -> {
                tokens.revokeFamily(token.familyId(), clock.instant());
                LOG.info("Logout: userId={} family={}", token.userId(), token.familyId());
            });
            return null;
        });
        return null;
    }
}
