package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.AssociationId;

/** Thrown when an association id doesn't exist (maps to "not found"). */
public class AssociationNotFoundException extends RuntimeException {

    private final AssociationId associationId;

    public AssociationNotFoundException(AssociationId associationId) {
        super("Association not found: " + associationId);
        this.associationId = associationId;
    }

    public AssociationId associationId() {
        return associationId;
    }
}
