package com.regivolley.api.application.command;

import java.util.Objects;

/** A visitor asks for the public page of the association with this short name (US-24). No actor: the page needs no account. */
public record GetPublicAssociationQuery(String shortName) {

    public GetPublicAssociationQuery {
        Objects.requireNonNull(shortName, "shortName must not be null");
    }
}
