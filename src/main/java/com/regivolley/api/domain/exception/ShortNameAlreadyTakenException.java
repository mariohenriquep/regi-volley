package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.ShortName;

/**
 * Thrown by the registration use case when another association already uses the short name
 * (US-01). Uniqueness spans associations, so it can only be checked through a repository, never
 * by the aggregate itself.
 */
public class ShortNameAlreadyTakenException extends BusinessRuleException {

    private final ShortName shortName;

    public ShortNameAlreadyTakenException(ShortName shortName) {
        super("The short name '" + shortName.value() + "' is already taken");
        this.shortName = shortName;
    }

    public ShortName shortName() {
        return shortName;
    }
}
