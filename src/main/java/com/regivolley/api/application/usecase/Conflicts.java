package com.regivolley.api.application.usecase;

import com.regivolley.api.application.exception.AccountAlreadyExistsException;
import com.regivolley.api.application.exception.LinkAlreadyIssuedException;
import com.regivolley.api.application.port.TransactionRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Repeats a credentials transaction that lost a race the database decided: the same email registered twice, or two links issued at
 * once. After a failed statement PostgreSQL aborts the transaction, so each attempt is a new one that re-reads what the winner left.
 */
final class Conflicts {

    private static final Logger LOG = LoggerFactory.getLogger(Conflicts.class);
    private static final int ATTEMPTS = 3;

    private Conflicts() {
    }

    static <T> T retrying(TransactionRunner transactions, Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.inNewTransaction(work);
            } catch (AccountAlreadyExistsException | LinkAlreadyIssuedException race) {
                if (attempt >= ATTEMPTS) {
                    throw race;
                }
                LOG.debug("Lost a race on credentials, retrying: attempt={} cause={}", attempt, race.getClass().getSimpleName());
            }
        }
    }
}
