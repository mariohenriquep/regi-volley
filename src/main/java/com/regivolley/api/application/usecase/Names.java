package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.VenueRepository;

import java.util.Map;
import java.util.stream.Collectors;

/** Display names the read models print beside an id, loaded in one query per association (architecture.md section 8). */
final class Names {

    private Names() {
    }

    /** The association's venues by id; a venue id that is not in it (no foreign key links a group to its venue) is simply absent. */
    static Map<VenueId, String> ofVenues(VenueRepository venues, AssociationId associationId) {
        return venues.findAllByAssociation(associationId).stream()
                .collect(Collectors.toMap(Venue::id, Venue::name, (first, second) -> first));
    }
}
