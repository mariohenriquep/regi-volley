package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.ShortName;

/**
 * Thrown when no association uses the short name a visitor addressed (maps to "not found"). A text that cannot be a short name at all
 * (wrong characters, too long) is just as "not found" as a well-formed one nobody uses, so a visitor learns nothing from the difference.
 */
public class ShortNameNotFoundException extends RuntimeException {

    private final String shortName;

    public ShortNameNotFoundException(ShortName shortName) {
        this(shortName.value());
    }

    /** The visitor's text as typed; it is never echoed to a client and never logged by the advice. */
    public ShortNameNotFoundException(String shortName) {
        super("No association uses that short name");
        this.shortName = shortName;
    }

    public String shortName() {
        return shortName;
    }
}
