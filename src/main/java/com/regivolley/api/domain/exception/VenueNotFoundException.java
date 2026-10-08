package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.VenueId;

/** Thrown when a venue id doesn't exist in the association it is addressed to (maps to "not found"). */
public class VenueNotFoundException extends RuntimeException {

    private final VenueId venueId;

    public VenueNotFoundException(VenueId venueId) {
        super("Venue not found in this association: " + venueId);
        this.venueId = venueId;
    }

    public VenueId venueId() {
        return venueId;
    }
}
