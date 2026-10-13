package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.MemberStatus;

import java.util.Objects;

/**
 * An administrator lists the association's members (US-07, US-08). {@code status} (null: any) and {@code text} (null or blank: no
 * search; otherwise matched against the name and the email, ignoring case) narrow the list.
 */
public record ListMembersQuery(Actor actor, MemberStatus status, String text) {

    public ListMembersQuery {
        Objects.requireNonNull(actor, "actor must not be null");
    }
}
