package com.regivolley.api.application.command;

import java.util.Objects;

/** "Send me a reset link" for this address. Personal data: {@link #toString()} prints nothing. */
public record RequestPasswordResetCommand(String email) {

    public RequestPasswordResetCommand {
        Objects.requireNonNull(email, "email must not be null");
    }

    @Override
    public String toString() {
        return "RequestPasswordResetCommand[redacted]";
    }
}
