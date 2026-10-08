package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.VenueModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.VenueRepository;
import com.regivolley.api.infrastructure.persistence.entity.VenueJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.VenuePersistenceMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** {@link VenueRepository} on Spring Data JPA; every save of an existing venue moves its version forward by one. */
@Component
public class VenueRepositoryAdapter implements VenueRepository {

    private final VenueJpaRepository venues;
    private final EntityManager entityManager;

    public VenueRepositoryAdapter(VenueJpaRepository venues, EntityManager entityManager) {
        this.venues = venues;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Venue> findById(AssociationId associationId, VenueId id) {
        return venues.findByIdAndAssociationId(id.value(), associationId.value()).map(VenuePersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<Venue> findByIdForUpdate(AssociationId associationId, VenueId id) {
        return venues.findForUpdateByIdAndAssociationId(id.value(), associationId.value())
                .map(VenuePersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Venue> findAllByAssociation(AssociationId associationId) {
        return venues.findAllByAssociationIdOrderByNameAscIdAsc(associationId.value()).stream()
                .map(VenuePersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public Venue save(Venue venue) {
        return WriteSupport.translatingConflicts(() -> VenuePersistenceMapper.toDomain(
                WriteSupport.write(venues, entityManager,
                        venues.findForUpdateByIdAndAssociationId(venue.id().value(), venue.associationId().value()),
                        venue.version(), VenueJpaEntity::new,
                        entity -> VenuePersistenceMapper.apply(venue, entity),
                        VenueJpaEntity::getVersion, () -> conflict(venue))),
                () -> conflict(venue));
    }

    @Override
    @Transactional
    public void delete(Venue venue) {
        WriteSupport.translatingConflicts(() -> {
            VenueJpaEntity stored = venues.findForUpdateByIdAndAssociationId(venue.id().value(), venue.associationId().value())
                    .orElseThrow(() -> conflict(venue));
            if (stored.getVersion() != venue.version()) {
                throw conflict(venue);
            }
            venues.delete(stored);
            venues.flush();
            return null;
        }, () -> conflict(venue));
    }

    private static VenueModifiedConcurrentlyException conflict(Venue venue) {
        return new VenueModifiedConcurrentlyException(venue.id());
    }
}
