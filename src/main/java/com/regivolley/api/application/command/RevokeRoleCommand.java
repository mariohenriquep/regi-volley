package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;

import java.util.Objects;

/** An administrator takes a role away from a member; the association keeps at least one active administrator. */
public record RevokeRoleCommand(Actor actor, MemberId memberId, MemberRole role) {

    public RevokeRoleCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
