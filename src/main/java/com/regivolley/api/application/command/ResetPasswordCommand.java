package com.regivolley.api.application.command;

import java.util.Objects;

/** The token of an emailed reset link and the new password. Credentials: {@link #toString()} prints nothing. */
public record ResetPasswordCommand(String token, String password) {

    public ResetPasswordCommand {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(password, "password must not be null");
    }

    @Override
    public String toString() {
        return "ResetPasswordCommand[redacted]";
    }
}
