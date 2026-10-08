package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.AssociationId;

import java.util.Objects;

/**
 * Generates the coming weeks' sessions of one association (US-10). A system operation: there is no
 * actor, the scheduler (or an administrator's trigger, once the web layer exists) supplies the tenant.
 */
public record GenerateSessionsCommand(AssociationId associationId) {

    public GenerateSessionsCommand {
        Objects.requireNonNull(associationId, "associationId must not be null");
    }
}
