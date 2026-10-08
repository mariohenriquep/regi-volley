package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.ShortName;

/** Thrown when no association uses the short name a visitor addressed (maps to "not found"). */
public class ShortNameNotFoundException extends RuntimeException {

    private final ShortName shortName;

    public ShortNameNotFoundException(ShortName shortName) {
        super("No association uses the short name '" + shortName.value() + "'");
        this.shortName = shortName;
    }

    public ShortName shortName() {
        return shortName;
    }
}
