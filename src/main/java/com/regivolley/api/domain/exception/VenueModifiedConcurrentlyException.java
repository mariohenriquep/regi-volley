package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.VenueId;

/** A venue was changed or deleted by a concurrent request between load and save, so this write was rejected and nothing was stored. */
public class VenueModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final VenueId venueId;

    public VenueModifiedConcurrentlyException(VenueId venueId) {
        super("venue", venueId.value());
        this.venueId = venueId;
    }

    public VenueId venueId() {
        return venueId;
    }
}
