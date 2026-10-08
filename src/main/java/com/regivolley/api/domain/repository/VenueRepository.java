package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.VenueModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.VenueId;

import java.util.List;
import java.util.Optional;

/** Port for {@link Venue} aggregates. Every read is scoped to one association (architecture.md section 8). */
public interface VenueRepository {

    Optional<Venue> findById(AssociationId associationId, VenueId id);

    /**
     * As {@link #findById}, but takes the venue's row lock for the rest of the current transaction. Creating a
     * group at a venue and deleting that venue both start with it, so they queue up: a group is never created
     * at a venue that is being deleted, and a venue is never deleted under a group that is being created. Must run
     * inside a transaction.
     */
    Optional<Venue> findByIdForUpdate(AssociationId associationId, VenueId id);

    /** All venues of the association, by name. */
    List<Venue> findAllByAssociation(AssociationId associationId);

    /**
     * Inserts a new venue or updates an existing one, and returns it as stored, with its new version (each save
     * moves it forward by one); keep working with the returned instance.
     *
     * @throws VenueModifiedConcurrentlyException if the stored venue is no longer at the version of {@code venue},
     *         if it has a version but no stored row in its association, or if its row could not be locked in time;
     *         nothing is written. Retry in a new transaction.
     */
    Venue save(Venue venue);

    /**
     * Deletes the venue. The caller has checked that no active group uses it.
     *
     * @throws VenueModifiedConcurrentlyException if the stored venue is no longer at the version of {@code venue}
     *         or is already gone (also when it belongs to another association); nothing is deleted.
     */
    void delete(Venue venue);
}
