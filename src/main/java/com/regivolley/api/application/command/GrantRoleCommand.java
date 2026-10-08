package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;

import java.util.Objects;

/** An administrator gives a member a role (COACH, ADMIN, ...). */
public record GrantRoleCommand(Actor actor, MemberId memberId, MemberRole role) {

    public GrantRoleCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }
}
