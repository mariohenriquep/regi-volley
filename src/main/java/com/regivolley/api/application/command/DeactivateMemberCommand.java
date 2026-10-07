package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/** An administrator deactivates a member (US-08); their future bookings are cancelled. */
public record DeactivateMemberCommand(Actor actor, MemberId memberId) {

    public DeactivateMemberCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
    }
}
