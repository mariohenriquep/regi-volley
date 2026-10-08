package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.VenueId;

import java.util.Objects;

/** An administrator deletes a venue no active training group uses (US-02). */
public record DeleteVenueCommand(Actor actor, VenueId venueId) {

    public DeleteVenueCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(venueId, "venueId must not be null");
    }
}
