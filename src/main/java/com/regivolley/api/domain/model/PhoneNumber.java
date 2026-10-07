package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.InvalidFieldException;

import java.util.regex.Pattern;

/**
 * A phone number: an optional leading {@code +} followed by 9 to 15 digits (spaces and dashes in
 * the input are dropped). Personal data: {@link #toString()} never prints it (architecture.md
 * section 11).
 */
public record PhoneNumber(String value) {

    private static final Pattern DIGITS = Pattern.compile("\\+?[0-9]{9,15}");

    /** Stand-in for an erased member's number. */
    static final PhoneNumber ANONYMISED = new PhoneNumber("000000000");

    public PhoneNumber {
        if (value == null || !DIGITS.matcher(value).matches()) {
            throw new InvalidFieldException("phone", "The phone number is not valid");
        }
    }

    /** Drops spaces and dashes, then validates raw user input. */
    public static PhoneNumber of(String raw) {
        if (raw == null) {
            throw new InvalidFieldException("phone", "The phone number is required");
        }
        return new PhoneNumber(raw.replaceAll("[\\s-]", ""));
    }

    @Override
    public String toString() {
        return "PhoneNumber[redacted]";
    }
}
