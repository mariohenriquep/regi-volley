package com.regivolley.api.application.usecase;

import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.exception.AggregateModifiedConcurrentlyException;
import com.regivolley.api.domain.port.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The retry contract of architecture.md section 10, in one place. Each attempt runs in a <em>new</em>
 * transaction and re-reads whatever it needs, so a lost race (any
 * {@link AggregateModifiedConcurrentlyException}) is decided against the up-to-date state: the booking
 * is then confirmed, waitlisted or rejected on what the winner left behind. After
 * {@value #MAX_ATTEMPTS} attempts the last conflict is rethrown (the web layer maps it to 409).
 *
 * <p>Notifications are sent after the commit and never undo it: a failing {@link Notifier} is
 * logged (by exception type only, since a message could carry an address) and swallowed.
 */
final class UnitOfWork {

    static final int MAX_ATTEMPTS = 5;

    private static final Logger LOG = LoggerFactory.getLogger(UnitOfWork.class);

    private final TransactionRunner transactions;

    UnitOfWork(TransactionRunner transactions) {
        this.transactions = transactions;
    }

    <T> T retrying(Supplier<T> attempt) {
        for (int number = 1; ; number++) {
            try {
                return transactions.inNewTransaction(attempt);
            } catch (AggregateModifiedConcurrentlyException conflict) {
                if (number >= MAX_ATTEMPTS) {
                    throw conflict;
                }
                LOG.debug("Attempt {} lost a race on {} {}, retrying", number, conflict.aggregate(), conflict.aggregateId());
            }
        }
    }

    <R> R retryingAndNotify(Supplier<Outcome<R>> attempt, Notifier notifier) {
        Outcome<R> committed = retrying(attempt);
        for (Consumer<Notifier> notification : committed.notifications()) {
            try {
                notification.accept(notifier);
            } catch (RuntimeException e) {
                LOG.warn("A notification failed after the commit: {}", e.getClass().getSimpleName());
            }
        }
        return committed.result();
    }
}
