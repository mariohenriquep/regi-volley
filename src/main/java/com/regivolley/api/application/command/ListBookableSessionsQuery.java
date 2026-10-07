package com.regivolley.api.application.command;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The sessions a member could book in one week (US-13). {@code dayInWeek} is any Lisbon calendar day
 * of the week wanted (the week runs Monday to Sunday); empty means the current week.
 */
public record ListBookableSessionsQuery(Actor actor, LocalDate dayInWeek) {

    public ListBookableSessionsQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
