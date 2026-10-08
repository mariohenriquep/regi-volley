package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;

/**
 * Creates and reconstitutes {@link Venue}s: the only place a venue is born. {@link Venue}'s constructor checks every
 * invariant, so neither path can produce an invalid venue. Stateless, so its methods are static.
 */
public final class VenueFactory {

    private VenueFactory() {
    }

    /** A new venue (US-02) with a generated id, at version 0. */
    public static Venue create(AssociationId associationId, String name, String address, int courts) {
        return new Venue(VenueId.generate(), associationId, name, address, courts, 0L);
    }

    /**
     * Rebuilds a venue from persisted data.
     *
     * @param version the optimistic-lock version it was loaded with (architecture.md section 10)
     */
    public static Venue reconstitute(VenueId id, AssociationId associationId, String name, String address,
                                     int courts, long version) {
        return new Venue(id, associationId, name, address, courts, version);
    }
}
