package com.regivolley.api.application.command;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The sessions the actor coaches - or, for an administrator, every session of the association - in one week (US-11, US-12, US-17).
 * {@code dayInWeek} is any Lisbon calendar day of the week wanted (the week runs Monday to Sunday); empty means the current week.
 */
public record ListCoachingSessionsQuery(Actor actor, LocalDate dayInWeek) {

    public ListCoachingSessionsQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
