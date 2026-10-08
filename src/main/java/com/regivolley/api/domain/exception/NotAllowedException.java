package com.regivolley.api.domain.exception;

/**
 * The actor may not do this: they are neither the owner of the booking nor the coach of the session
 * nor an administrator (architecture.md section 11). The message names the action, never a person.
 */
public class NotAllowedException extends BusinessRuleException {

    public NotAllowedException(String action) {
        super("You are not allowed to " + action);
    }
}
