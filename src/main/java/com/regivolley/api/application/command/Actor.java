package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/**
 * Who is performing an operation, as resolved from the authenticated principal by the
 * infrastructure (architecture.md sections 8 and 11): the tenant and the member inside it. The
 * tenant of every use case comes from here, never from a request body. The actor's roles are
 * read from their {@code Member} by the use case when it needs them.
 */
public record Actor(AssociationId associationId, MemberId memberId) {

    public Actor {
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
    }
}
