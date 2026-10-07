package com.regivolley.api.domain.exception;

/** Thrown when an association would be registered without any level (US-03). */
public class AtLeastOneLevelRequiredException extends BusinessRuleException {

    public AtLeastOneLevelRequiredException() {
        super("An association needs at least one level");
    }
}
