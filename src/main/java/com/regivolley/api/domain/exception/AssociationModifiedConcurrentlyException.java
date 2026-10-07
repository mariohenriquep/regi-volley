package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.AssociationId;

/**
 * An association (its details, policies or levels) was changed by a concurrent request between load
 * and save, so this write was rejected and nothing was stored.
 */
public class AssociationModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final AssociationId associationId;

    public AssociationModifiedConcurrentlyException(AssociationId associationId) {
        super("association", associationId.value());
        this.associationId = associationId;
    }

    public AssociationId associationId() {
        return associationId;
    }
}
