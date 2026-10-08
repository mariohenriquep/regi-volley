package com.regivolley.api.application.command;

import java.util.Objects;

/** A member's own plans, balance and payment status (US-23). */
public record MyPlanQuery(Actor actor) {

    public MyPlanQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
