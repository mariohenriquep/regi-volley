package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/** A coach or an administrator moves a member to another level of the association (US-07, RN-20). */
public record ChangeMemberLevelCommand(Actor actor, MemberId memberId, LevelId levelId) {

    public ChangeMemberLevelCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(levelId, "levelId must not be null");
    }
}
