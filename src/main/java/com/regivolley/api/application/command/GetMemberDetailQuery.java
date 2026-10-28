package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/** An administrator opens one member of their association: contact data, level history and subscriptions (US-04, US-07, US-20). */
public record GetMemberDetailQuery(Actor actor, MemberId memberId) {

    public GetMemberDetailQuery {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
    }
}
