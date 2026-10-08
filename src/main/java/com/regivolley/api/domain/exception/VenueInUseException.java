package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.VenueId;

/** Thrown when deleting a venue that an active training group still uses (US-02). */
public class VenueInUseException extends BusinessRuleException {

    private final VenueId venueId;

    public VenueInUseException(VenueId venueId) {
        super("The venue is used by an active training group and cannot be deleted");
        this.venueId = venueId;
    }

    public VenueId venueId() {
        return venueId;
    }
}
