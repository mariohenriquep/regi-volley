package com.regivolley.api.domain.exception;

/**
 * Base of every rule violation a user can trigger (booking window closed, duplicate booking,
 * ...). Subclasses carry structured data plus an English message the web layer may show as-is;
 * times in messages are formatted in Europe/Lisbon (architecture.md section 12). Invariant and
 * programming errors do NOT extend this: they are English, never shown to users.
 */
public abstract class BusinessRuleException extends RuntimeException {

    protected BusinessRuleException(String message) {
        super(message);
    }
}
