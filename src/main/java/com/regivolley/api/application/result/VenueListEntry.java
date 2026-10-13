package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Venue;

import java.util.Objects;

/** A venue with whether an ACTIVE training group runs there, which is what makes deleting it refused (US-02); information only. */
public record VenueListEntry(Venue venue, boolean inUseByActiveGroup) {

    public VenueListEntry {
        Objects.requireNonNull(venue, "venue must not be null");
    }
}
