package com.regivolley.api.domain.exception;

/** Thrown when two levels of one association would share a name (US-03), ignoring case. */
public class DuplicateLevelNameException extends BusinessRuleException {

    private final String name;

    public DuplicateLevelNameException(String name) {
        super("The association already has a level named '" + name + "'");
        this.name = name;
    }

    public String name() {
        return name;
    }
}
