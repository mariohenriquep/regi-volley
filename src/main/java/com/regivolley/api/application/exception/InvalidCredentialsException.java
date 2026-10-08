package com.regivolley.api.application.exception;

/**
 * The login failed. One type and one message for every reason - unknown email, wrong password, account not activated, disabled,
 * membership pending, member inactive - so nothing in the answer tells them apart (threat model D-10). The real reason goes to
 * the audit log, by id.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
