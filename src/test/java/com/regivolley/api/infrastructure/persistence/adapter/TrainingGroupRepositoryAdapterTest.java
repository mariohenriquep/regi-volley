package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.Venue;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.exception.AggregateModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.util.Set;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.group;
import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.slot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class TrainingGroupRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TrainingGroupRepository groups;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Association newAssociation() {
        return associations.save(Fixtures.association());
    }

    private static Set<LevelId> twoLevels(Association association) {
        return Set.of(association.levels().get(1).id(), association.levels().get(2).id());
    }

    private TrainingGroup saveAndReload(TrainingGroup group) {
        TrainingGroup saved = groups.save(group);
        flushAndClear();
        return groups.findById(saved.associationId(), saved.id()).orElseThrow();
    }

    private Long storedVersion(TrainingGroup group) {
        return jdbc.queryForObject("select version from training_groups where id = ?", Long.class, group.id().value());
    }

    @Test
    void aNewGroupComesBackWithScheduleAcceptedLevelsAndAllFields() {
        // Arrange
        Association association = newAssociation();
        TrainingGroup group = group(association.id(), "Open play", twoLevels(association), MemberId.generate());

        // Act
        TrainingGroup loaded = saveAndReload(group);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(group);
        assertThat(loaded.schedule().slots()).hasSize(2);
        assertThat(loaded.acceptedLevels()).hasSize(2);
        assertThat(loaded.version()).isZero();
    }

    @Test
    void anArchivedGroupKeepsItsStatus() {
        // Arrange
        Association association = newAssociation();
        TrainingGroup archived = group(association.id(), "Old", twoLevels(association), MemberId.generate()).archive();

        // Act
        TrainingGroup loaded = saveAndReload(archived);

        // Assert
        assertThat(loaded.status()).isEqualTo(TrainingGroupStatus.ARCHIVED);
    }

    @Test
    void editedScheduleLevelsAndCoachReplaceTheStoredOnes() {
        // Arrange
        Association association = newAssociation();
        TrainingGroup stored = groups.save(group(association.id(), "Open play", twoLevels(association), MemberId.generate()));
        flushAndClear();
        TrainingGroup loaded = groups.findById(association.id(), stored.id()).orElseThrow();
        MemberId newCoach = MemberId.generate();
        TrainingGroup edited = loaded
                .changeSchedule(WeeklySchedule.of(slot(DayOfWeek.FRIDAY, 19, 120)))
                .changeAcceptedLevels(Set.of(association.levels().get(0).id()))
                .changeCoach(newCoach)
                .rename("Friday night");

        // Act
        TrainingGroup saved = groups.save(edited);
        flushAndClear();
        TrainingGroup reloaded = groups.findById(association.id(), stored.id()).orElseThrow();

        // Assert
        assertThat(reloaded).usingRecursiveComparison().ignoringFields("version").isEqualTo(saved);
        assertThat(reloaded.schedule().slots()).hasSize(1);
        assertThat(reloaded.acceptedLevels()).containsExactly(association.levels().get(0).id());
        assertThat(reloaded.coachId()).isEqualTo(newCoach);
        assertThat(jdbc.queryForObject("select count(*) from training_group_slots where training_group_id = ?",
                Integer.class, stored.id().value())).isEqualTo(1);
    }

    @Test
    void everySaveMovesTheVersionByExactlyOne() {
        // Arrange
        Association association = newAssociation();
        TrainingGroup stored = groups.save(group(association.id(), "Open play", twoLevels(association), MemberId.generate()));
        flushAndClear();
        TrainingGroup loaded = groups.findById(association.id(), stored.id()).orElseThrow();

        // Act
        TrainingGroup columnChange = groups.save(loaded.changeCapacity(8));
        flushAndClear();
        TrainingGroup childOnlyChange = groups.save(
                groups.findById(association.id(), stored.id()).orElseThrow()
                        .changeAcceptedLevels(Set.of(association.levels().get(0).id())));
        flushAndClear();

        // Assert
        assertThat(columnChange.version()).isEqualTo(1L);
        assertThat(childOnlyChange.version()).isEqualTo(2L);
        assertThat(storedVersion(childOnlyChange)).isEqualTo(2L);
    }

    @Test
    void aStaleCopyIsRejectedAndTheOtherEditIsNotLost() {
        // Arrange
        Association association = newAssociation();
        TrainingGroup stored = groups.save(group(association.id(), "Open play", twoLevels(association), MemberId.generate()));
        flushAndClear();
        TrainingGroup copyA = groups.findById(association.id(), stored.id()).orElseThrow();
        TrainingGroup copyB = groups.findById(association.id(), stored.id()).orElseThrow();
        groups.save(copyB.archive());
        flushAndClear();
        Executable act = () -> groups.save(copyA.rename("Renamed after archive"));

        // Act
        AggregateModifiedConcurrentlyException ex = assertThrows(AggregateModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.aggregateId()).isEqualTo(stored.id().value());
        assertThat(groups.findById(association.id(), stored.id()).orElseThrow().status())
                .isEqualTo(TrainingGroupStatus.ARCHIVED);
    }

    @Test
    void aGroupIsInvisibleToAnotherAssociationByIdAndInTheList() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        TrainingGroup ofA = groups.save(group(a.id(), "Zeta", twoLevels(a), MemberId.generate()));
        TrainingGroup ofA2 = groups.save(group(a.id(), "Alpha", twoLevels(a), MemberId.generate()));
        TrainingGroup ofB = groups.save(group(b.id(), "Beta", twoLevels(b), MemberId.generate()));
        flushAndClear();

        // Act
        boolean visibleToB = groups.findById(b.id(), ofA.id()).isPresent();
        boolean visibleToA = groups.findById(a.id(), ofB.id()).isPresent();

        // Assert
        assertThat(visibleToB).isFalse();
        assertThat(visibleToA).isFalse();
        assertThat(groups.findAllByAssociation(a.id())).extracting(TrainingGroup::id).containsExactly(ofA2.id(), ofA.id());
        assertThat(groups.findAllByAssociation(b.id())).extracting(TrainingGroup::id).containsExactly(ofB.id());
        assertThat(groups.findAllByAssociation(AssociationId.generate())).isEmpty();
    }

    @Test
    void existsActiveWithVenueIsTrueOnlyForAnActiveGroupOfThatAssociationAtThatVenue() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        TrainingGroup active = groups.save(group(a.id(), "Active", twoLevels(a), MemberId.generate()));
        TrainingGroup archived = groups.save(group(a.id(), "Archived", twoLevels(a), MemberId.generate()).archive());
        flushAndClear();

        // Act
        boolean activeVenue = groups.existsActiveWithVenue(a.id(), active.venueId());
        boolean archivedVenue = groups.existsActiveWithVenue(a.id(), archived.venueId());
        boolean asOtherAssociation = groups.existsActiveWithVenue(b.id(), active.venueId());
        boolean unknownVenue = groups.existsActiveWithVenue(a.id(), VenueId.generate());

        // Assert
        assertThat(activeVenue).isTrue();
        assertThat(archivedVenue).isFalse();
        assertThat(asOtherAssociation).isFalse();
        assertThat(unknownVenue).isFalse();
    }
}
