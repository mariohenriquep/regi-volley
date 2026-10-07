package com.regivolley.api.domain.exception;

/** Thrown when a training group would accept no level at all (RN-21). */
public class AtLeastOneAcceptedLevelRequiredException extends BusinessRuleException {

    public AtLeastOneAcceptedLevelRequiredException() {
        super("A training group must accept at least one level");
    }
}
