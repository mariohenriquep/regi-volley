package com.regivolley.api.application.command;

import java.util.Objects;

/** A member's own booking history and no-show standing (US-18). */
public record MemberHistoryQuery(Actor actor) {

    public MemberHistoryQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
