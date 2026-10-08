package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.VenueModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.VenueRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class VenueRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private VenueRepository venues;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Association newAssociation() {
        return associations.save(Fixtures.association());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Venue saveAndReload(Venue venue) {
        Venue saved = venues.save(venue);
        flushAndClear();
        return venues.findById(saved.associationId(), saved.id()).orElseThrow();
    }

    @Test
    void aNewVenueComesBackWithAllItsFields() {
        // Arrange
        Association association = newAssociation();
        Venue venue = Venue.create(association.id(), "Pavilhao Central", "Rua A 1, Lisboa", 2);

        // Act
        Venue loaded = saveAndReload(venue);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(venue);
        assertThat(loaded.courts()).isEqualTo(2);
    }

    @Test
    void savingAgainUpdatesTheVenueInPlaceAndMovesTheVersionByOne() {
        // Arrange
        Association association = newAssociation();
        Venue stored = venues.save(Venue.create(association.id(), "Old", "Old street", 1));
        flushAndClear();
        Venue edited = venues.findById(association.id(), stored.id()).orElseThrow().edit("New", "New street", 3);

        // Act
        Venue loaded = saveAndReload(edited);

        // Assert
        assertThat(stored.version()).isZero();
        assertThat(loaded.version()).isEqualTo(1L);
        assertThat(loaded.name()).isEqualTo("New");
        assertThat(loaded.address()).isEqualTo("New street");
        assertThat(loaded.courts()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from venues where id = ?", Integer.class, stored.id().value())).isEqualTo(1);
    }

    @Test
    void aStaleCopyIsRejectedAndTheOtherEditIsNotLost() {
        // Arrange
        Association association = newAssociation();
        Venue stored = venues.save(Venue.create(association.id(), "Venue", "Street", 1));
        flushAndClear();
        Venue copyA = venues.findById(association.id(), stored.id()).orElseThrow();
        Venue copyB = venues.findById(association.id(), stored.id()).orElseThrow();
        venues.save(copyB.edit("Winner", "Street", 1));
        flushAndClear();
        Executable act = () -> venues.save(copyA.edit("Loser", "Street", 1));

        // Act
        VenueModifiedConcurrentlyException ex = assertThrows(VenueModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.venueId()).isEqualTo(stored.id());
        assertThat(venues.findById(association.id(), stored.id()).orElseThrow().name()).isEqualTo("Winner");
    }

    @Test
    void aVenueIsInvisibleToAnotherAssociationByIdAndInTheList() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Venue ofA = venues.save(Venue.create(a.id(), "Zeta", "Street", 1));
        Venue ofA2 = venues.save(Venue.create(a.id(), "Alpha", "Street", 1));
        Venue ofB = venues.save(Venue.create(b.id(), "Beta", "Street", 1));
        flushAndClear();

        // Act
        boolean visibleToB = venues.findById(b.id(), ofA.id()).isPresent();
        boolean lockedAsB = venues.findByIdForUpdate(b.id(), ofA.id()).isPresent();

        // Assert
        assertThat(visibleToB).isFalse();
        assertThat(lockedAsB).isFalse();
        assertThat(venues.findByIdForUpdate(a.id(), ofA.id())).isPresent();
        assertThat(venues.findAllByAssociation(a.id())).extracting(Venue::id).containsExactly(ofA2.id(), ofA.id());
        assertThat(venues.findAllByAssociation(b.id())).extracting(Venue::id).containsExactly(ofB.id());
    }

    @Test
    void deletingRemovesTheVenueOnlyInItsOwnAssociation() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Venue stored = venues.save(Venue.create(a.id(), "Venue", "Street", 1));
        flushAndClear();
        Venue asLoaded = venues.findById(a.id(), stored.id()).orElseThrow();
        Venue disguised = Venue.reconstruct(stored.id(), b.id(), "Venue", "Street", 1, asLoaded.version());
        Executable foreignDelete = () -> venues.delete(disguised);

        // Act
        assertThrows(VenueModifiedConcurrentlyException.class, foreignDelete);
        venues.delete(asLoaded);
        flushAndClear();

        // Assert
        assertThat(venues.findById(a.id(), stored.id())).isEmpty();
    }

    @Test
    void deletingWithAStaleCopyOrTwiceIsAConflict() {
        // Arrange
        Association association = newAssociation();
        Venue stored = venues.save(Venue.create(association.id(), "Venue", "Street", 1));
        flushAndClear();
        Venue stale = venues.findById(association.id(), stored.id()).orElseThrow();
        venues.save(venues.findById(association.id(), stored.id()).orElseThrow().edit("Venue", "Other street", 1));
        flushAndClear();
        Executable staleDelete = () -> venues.delete(stale);

        // Act
        assertThrows(VenueModifiedConcurrentlyException.class, staleDelete);
        Venue fresh = venues.findById(association.id(), stored.id()).orElseThrow();
        venues.delete(fresh);
        flushAndClear();
        Executable secondDelete = () -> venues.delete(fresh);

        // Assert
        assertThrows(VenueModifiedConcurrentlyException.class, secondDelete);
    }
}
