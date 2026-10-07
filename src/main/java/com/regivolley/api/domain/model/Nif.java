package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.InvalidFieldException;

/**
 * Portuguese tax number (NIF): 9 digits whose last one is a mod-11 check digit over the first
 * eight, weighted 9 down to 2, starting with a digit from 1 to 9 other than 4 (0 is never issued;
 * 4 is only used by the 45 non-resident prefix, which does not apply to associations). Only the
 * format and check digit are verified; whether the number is actually
 * issued is outside the domain. Optional on an association (US-01).
 */
public record Nif(String value) {

    private static final int LENGTH = 9;

    public Nif {
        if (value == null || !isValid(value)) {
            throw new InvalidFieldException("NIF", "The NIF is not valid");
        }
    }

    /** Strips inner spaces, then validates. */
    public static Nif of(String raw) {
        if (raw == null) {
            throw new InvalidFieldException("NIF", "The NIF is not valid");
        }
        return new Nif(raw.replace(" ", "").trim());
    }

    private static boolean isValid(String digits) {
        if (digits.length() != LENGTH || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        if (digits.charAt(0) == '0' || digits.charAt(0) == '4') {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < LENGTH - 1; i++) {
            sum += (digits.charAt(i) - '0') * (LENGTH - i);
        }
        int check = 11 - sum % 11;
        if (check >= 10) {
            check = 0;
        }
        return check == digits.charAt(LENGTH - 1) - '0';
    }

    @Override
    public String toString() {
        return value;
    }
}
