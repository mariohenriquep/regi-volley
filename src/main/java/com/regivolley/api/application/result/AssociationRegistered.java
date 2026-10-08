package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.Objects;

/** Outcome of registering an association (US-01): the new tenant and its founder, who is its first administrator. */
public record AssociationRegistered(AssociationId associationId, MemberId founderId) {

    public AssociationRegistered {
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(founderId, "founderId must not be null");
    }
}
