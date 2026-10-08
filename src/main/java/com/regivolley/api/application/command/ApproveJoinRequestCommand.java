package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.JoinRequestId;

import java.util.Objects;

/** An administrator approves a pending join request: the person becomes a member at the entry level (US-06). */
public record ApproveJoinRequestCommand(Actor actor, JoinRequestId requestId) {

    public ApproveJoinRequestCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(requestId, "requestId must not be null");
    }
}
