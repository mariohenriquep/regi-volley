package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.VenueId;

import java.util.Objects;

/** Outcome of deleting a venue (US-02). */
public record VenueDeleted(VenueId venueId) {

    public VenueDeleted {
        Objects.requireNonNull(venueId, "venueId must not be null");
    }
}
