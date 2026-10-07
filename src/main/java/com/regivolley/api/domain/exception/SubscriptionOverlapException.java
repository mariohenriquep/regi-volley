package com.regivolley.api.domain.exception;

import java.time.LocalDate;

/** Thrown when a new subscription's period overlaps one the member already has (RN-16). */
public class SubscriptionOverlapException extends BusinessRuleException {

    private final LocalDate conflictStart;
    private final LocalDate conflictEnd;

    public SubscriptionOverlapException(LocalDate conflictStart, LocalDate conflictEnd) {
        super("The member already has a subscription from " + LisbonTimeFormat.formatDate(conflictStart)
                + " to " + LisbonTimeFormat.formatDate(conflictEnd) + " that overlaps the requested period");
        this.conflictStart = conflictStart;
        this.conflictEnd = conflictEnd;
    }

    /** First day of the existing subscription that conflicts. */
    public LocalDate conflictStart() {
        return conflictStart;
    }

    /** Last day (inclusive) of the existing subscription that conflicts. */
    public LocalDate conflictEnd() {
        return conflictEnd;
    }
}
