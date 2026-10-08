package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.JoinRequestId;

import java.util.Objects;

/** An administrator rejects a pending join request (US-06); {@code reason} is optional (null or blank means none). */
public record RejectJoinRequestCommand(Actor actor, JoinRequestId requestId, String reason) {

    public RejectJoinRequestCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
