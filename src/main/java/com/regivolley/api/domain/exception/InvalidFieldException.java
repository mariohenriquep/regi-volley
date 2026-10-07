package com.regivolley.api.domain.exception;

/**
 * Thrown when a value a user typed (name, email, phone, NIF, short name, ...) is missing or
 * malformed. A rule violation a user can trigger, so it carries the field name and an English
 * message the web layer may show as-is. Never carries the rejected value itself: it may be
 * personal data (architecture.md section 11).
 */
public class InvalidFieldException extends BusinessRuleException {

    private final String field;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
