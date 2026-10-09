package com.regivolley.api.domain.model.valueobject;

import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.shared.ValueObject;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An association's unique short name (US-01), used in its public URL (US-24): a slug of lowercase
 * letters, digits and single hyphens, 3 to 40 characters, starting and ending with a letter or
 * digit. Input is trimmed and lowercased before validation. Uniqueness across associations is a
 * repository concern (see {@code ShortNameAlreadyTakenException}).
 */
public record ShortName(String value) implements ValueObject {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 40;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public ShortName {
        if (value == null || !SLUG.matcher(value).matches()
                || value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new InvalidFieldException("short name", "The short name must have " + MIN_LENGTH + " to "
                    + MAX_LENGTH + " characters: lowercase letters, digits and single hyphens, not starting or ending with a hyphen");
        }
    }

    /** Normalises (trim, lowercase) and validates raw user input. */
    public static ShortName of(String raw) {
        if (raw == null) {
            throw new InvalidFieldException("short name", "The short name is required");
        }
        return new ShortName(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** The short name a visitor addressed, or empty when the text cannot be one (so it names no association). */
    public static Optional<ShortName> tryOf(String raw) {
        try {
            return Optional.of(of(raw));
        } catch (InvalidFieldException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
