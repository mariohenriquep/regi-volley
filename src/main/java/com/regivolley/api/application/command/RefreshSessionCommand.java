package com.regivolley.api.application.command;

/** The refresh token the caller presents (from the cookie); {@code null} when there was none. A credential: never printed. */
public record RefreshSessionCommand(String refreshToken) {

    @Override
    public String toString() {
        return "RefreshSessionCommand[redacted]";
    }
}
