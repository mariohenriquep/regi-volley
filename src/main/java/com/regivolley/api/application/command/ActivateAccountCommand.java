package com.regivolley.api.application.command;

import java.util.Objects;

/** The token of an emailed activation link and the first password. Credentials: {@link #toString()} prints nothing. */
public record ActivateAccountCommand(String token, String password) {

    public ActivateAccountCommand {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }

    @Override
    public String toString() {
        return "ActivateAccountCommand[redacted]";
    }
}
