package com.regivolley.api.application.command;

/** End the login the presented refresh token belongs to; {@code null} when there was none. A credential: never printed. */
public record LogoutCommand(String refreshToken) {

    @Override
    public String toString() {
        return "LogoutCommand[redacted]";
    }
}
