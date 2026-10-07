package com.regivolley.api.domain.exception;

import java.util.UUID;

/**
 * Raised by a repository when an aggregate was changed by someone else between the moment it was
 * loaded and the moment it is saved (optimistic locking, architecture.md section 10), or could not
 * be locked in time, or no longer exists in this association. Nothing was written. The caller
 * reloads and retries in a new transaction, or reports the conflict; it is not a rule violation of
 * the user, so it does not extend {@link BusinessRuleException}.
 *
 * <p>Every versioned aggregate has its own subclass, so a use case catches the one it works with.
 * Carries ids only: no personal data.
 */
public abstract class AggregateModifiedConcurrentlyException extends RuntimeException {

    private final String aggregate;
    private final UUID aggregateId;

    protected AggregateModifiedConcurrentlyException(String aggregate, UUID aggregateId) {
        super("The " + aggregate + " " + aggregateId + " was modified concurrently");
        this.aggregate = aggregate;
        this.aggregateId = aggregateId;
    }

    /** Which kind of aggregate conflicted, e.g. "session" or "training group". */
    public String aggregate() {
        return aggregate;
    }

    public UUID aggregateId() {
        return aggregateId;
    }
}
