package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/** An administrator asks for the activation link of a member who has not activated their account to be sent again (threat model section 6, P7). */
public record ResendActivationLinkCommand(Actor actor, MemberId memberId) {

    public ResendActivationLinkCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
    }
}
