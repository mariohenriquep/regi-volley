package com.regivolley.api.infrastructure.security;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;
import java.util.UUID;

/**
 * The caller of a request, once the token has been verified and re-checked against the database (threat model D-6):
 * the account, and the association and member of the selected membership. It is the principal of the security context;
 * controllers receive it with {@link CurrentActor} and call {@link #actor()}, the one place an {@link Actor} is built.
 * Ids only: no roles (use cases read them from the {@code Member}) and no personal data.
 */
public record AuthenticatedActor(UUID userId, AssociationId associationId, MemberId memberId) {

    public AuthenticatedActor {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
    }

    /** The identity the application layer sees. The only {@code new Actor(...)} in production code (architecture.md section 11). */
    public Actor actor() {
        return new Actor(associationId, memberId);
    }
}
