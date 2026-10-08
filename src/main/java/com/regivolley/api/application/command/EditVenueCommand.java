package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.VenueId;

import java.util.Objects;

/** An administrator changes a venue's name, address or number of courts (US-02). */
public record EditVenueCommand(Actor actor, VenueId venueId, String name, String address, int courts) {

    public EditVenueCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(venueId, "venueId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(address, "address must not be null");
    }
}
