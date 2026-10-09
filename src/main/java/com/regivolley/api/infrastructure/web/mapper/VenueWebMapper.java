package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.CreateVenueCommand;
import com.regivolley.api.application.command.DeleteVenueCommand;
import com.regivolley.api.application.command.EditVenueCommand;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.infrastructure.web.dto.VenueRequest;
import com.regivolley.api.infrastructure.web.dto.VenueResponse;

import java.util.UUID;

/** Venues (US-02). */
public final class VenueWebMapper {

    private VenueWebMapper() {
    }

    public static CreateVenueCommand createCommand(Actor actor, VenueRequest body) {
        return new CreateVenueCommand(actor, body.name(), body.address(), body.courts());
    }

    public static EditVenueCommand editCommand(Actor actor, UUID venueId, VenueRequest body) {
        return new EditVenueCommand(actor, VenueId.of(venueId), body.name(), body.address(), body.courts());
    }

    public static DeleteVenueCommand deleteCommand(Actor actor, UUID venueId) {
        return new DeleteVenueCommand(actor, VenueId.of(venueId));
    }

    public static VenueResponse toResponse(Venue venue) {
        return new VenueResponse(venue.id().value(), venue.name(), venue.address(), venue.courts());
    }
}
