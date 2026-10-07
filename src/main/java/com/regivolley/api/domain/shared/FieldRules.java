package com.regivolley.api.domain.shared;

import com.regivolley.api.domain.exception.InvalidFieldException;

/** Shared validation of user-typed text, so every aggregate words and bounds it the same way. */
public final class FieldRules {

    private FieldRules() {
    }

    /** Trims {@code value}; it must then be non-empty and at most {@code maxLength} characters. */
    public static String requiredText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidFieldException(field, "The " + field + " is required");
        }
        String trimmed = value.trim();
        requireMaxLength(field, trimmed, maxLength);
        return trimmed;
    }

    public static void requireMaxLength(String field, String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new InvalidFieldException(field, "The " + field + " must have at most " + maxLength + " characters");
        }
    }
}
