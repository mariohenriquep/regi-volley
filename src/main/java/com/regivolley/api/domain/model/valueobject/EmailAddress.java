package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.shared.ValueObject;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An email address, checked only for basic shape (one {@code @}, a dotted domain, no spaces, at
 * most 254 characters) and normalised to lowercase. Personal data: {@link #toString()} never
 * prints it (architecture.md section 11).
 */
public record EmailAddress(String value) implements ValueObject {

    public static final int MAX_LENGTH = 254;

    private static final Pattern BASIC_SHAPE = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");

    public EmailAddress {
        if (value == null || value.length() > MAX_LENGTH || !BASIC_SHAPE.matcher(value).matches()) {
            throw new InvalidFieldException("email", "The email address is not valid");
        }
    }

    /** Trims, lowercases and validates raw user input. */
    public static EmailAddress of(String raw) {
        if (raw == null) {
            throw new InvalidFieldException("email", "The email address is required");
        }
        return new EmailAddress(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** Stand-in for an erased record's address: unique per record, on the reserved {@code .invalid} TLD. */
    static EmailAddress anonymisedFor(UUID recordId) {
        return new EmailAddress("anonymised-" + recordId + "@anonymised.invalid");
    }

    @Override
    public String toString() {
        return "EmailAddress[redacted]";
    }
}
