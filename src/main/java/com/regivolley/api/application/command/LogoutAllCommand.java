package com.regivolley.api.application.command;

import java.util.Objects;
import java.util.UUID;

/** End every login of the authenticated account. */
public record LogoutAllCommand(UUID userId) {

    public LogoutAllCommand {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
