package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.infrastructure.persistence.entity.VenueJpaEntity;

/** Translates a {@link Venue} to and from {@link VenueJpaEntity}. */
public final class VenuePersistenceMapper {

    private VenuePersistenceMapper() {
    }

    /** Rebuilds the aggregate, re-checking its invariants. */
    public static Venue toDomain(VenueJpaEntity entity) {
        return Venue.reconstruct(new VenueId(entity.getId()), new AssociationId(entity.getAssociationId()),
                entity.getName(), entity.getAddress(), entity.getCourts(),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /** Copies the venue onto {@code entity} (new, or loaded and managed). The version stays with the persistence layer. */
    public static void apply(Venue venue, VenueJpaEntity entity) {
        entity.setId(venue.id().value());
        entity.setAssociationId(venue.associationId().value());
        entity.setName(venue.name());
        entity.setAddress(venue.address());
        entity.setCourts(venue.courts());
    }
}
