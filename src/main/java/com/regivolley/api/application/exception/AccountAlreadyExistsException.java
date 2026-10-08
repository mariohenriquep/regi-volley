package com.regivolley.api.application.exception;

/** An account with that email already exists (the unique index decided a race the caller's lookup could not rule out). Carries no data. */
public class AccountAlreadyExistsException extends RuntimeException {

    public AccountAlreadyExistsException() {
        super("An account with that email already exists");
    }
}
