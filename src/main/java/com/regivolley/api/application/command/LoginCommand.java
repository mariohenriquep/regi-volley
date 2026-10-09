package com.regivolley.api.application.command;

import java.util.Objects;

/**
 * Email and password to sign in, with the caller's network address (only used, truncated, in the audit line of a failure). A
 * credential: {@link #toString()} prints nothing.
 */
public record LoginCommand(String email, String password, String clientAddress) {

    public LoginCommand {
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }

    @Override
    public String toString() {
        return "LoginCommand[redacted]";
    }
}
