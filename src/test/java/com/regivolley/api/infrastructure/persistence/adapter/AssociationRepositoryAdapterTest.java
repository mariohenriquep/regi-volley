package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.AggregateModifiedConcurrentlyException;
import com.regivolley.api.domain.exception.ShortNameAlreadyTakenException;
import com.regivolley.api.domain.factory.AssociationFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.NoShowPolicy;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.ShortName;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static com.regivolley.api.infrastructure.persistence.adapter.Fixtures.association;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class AssociationRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

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

    private Association saveAndReload(Association association) {
        Association saved = associations.save(association);
        flushAndClear();
        return associations.findById(saved.id()).orElseThrow();
    }

    private Long storedVersion(Association association) {
        return jdbc.queryForObject("select version from associations where id = ?", Long.class, association.id().value());
    }

    private static LevelId levelNamed(Association association, String name) {
        return association.levels().stream().filter(level -> level.name().equals(name)).findFirst().orElseThrow().id();
    }

    @Test
    void aNewAssociationComesBackWithAllItsFieldsAndLevels() {
        // Arrange
        Association association = association("porto-volley");

        // Act
        Association loaded = saveAndReload(association);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(association);
        assertThat(loaded.version()).isZero();
        assertThat(loaded.sessionGenerationPolicy()).isEqualTo(SessionGenerationPolicy.defaults());
        assertThat(loaded.noShowPolicy()).isEqualTo(NoShowPolicy.defaults());
        assertThat(loaded.levels()).extracting(Level::name).containsExactly("Beginner", "Intermediate", "Advanced");
    }

    @Test
    void anAssociationWithoutNifAndWithCustomPoliciesAndANonLowestEntryLevelRoundTrips() {
        // Arrange
        Association base = AssociationFactory.create("Club Sem Nif", "sem-nif", null, "Porto", "a@b.co",
                List.of("Beginner", "Advanced"));
        Association custom = AssociationFactory.reconstitute(base.id(), base.name(), base.shortName(), null, base.locality(),
                base.contactEmail(), new BookingPolicy(3, 12), new SessionGenerationPolicy(6), new NoShowPolicy(5), base.levels(),
                levelNamed(base, "Advanced"), 0L);

        // Act
        Association loaded = saveAndReload(custom);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(custom);
        assertThat(loaded.nif()).isEmpty();
        assertThat(loaded.bookingPolicy()).isEqualTo(new BookingPolicy(3, 12));
        assertThat(loaded.sessionGenerationPolicy().windowWeeks()).isEqualTo(6);
        assertThat(loaded.noShowPolicy()).isEqualTo(new NoShowPolicy(5));
        assertThat(loaded.entryLevel().name()).isEqualTo("Advanced");
    }

    @Test
    void listsTheIdsOfEveryStoredAssociation() {
        // Arrange
        Association first = associations.save(association());
        Association second = associations.save(association());
        flushAndClear();

        // Act
        List<AssociationId> ids = associations.findAllIds();

        // Assert
        assertThat(ids).contains(first.id(), second.id());
    }

    @Test
    void levelEditsArePersistedInPlaceAndMoveTheVersionByOne() {
        // Arrange
        Association stored = associations.save(association());
        flushAndClear();
        Association loaded = associations.findById(stored.id()).orElseThrow();
        Association withPro = loaded.addLevel("Pro").renameLevel(levelNamed(loaded, "Beginner"), "Newcomer");
        Association edited = withPro.reorderLevels(List.of(levelNamed(withPro, "Pro"), levelNamed(withPro, "Advanced"),
                levelNamed(withPro, "Intermediate"), levelNamed(withPro, "Newcomer")));

        // Act
        Association saved = associations.save(edited.changeEntryLevel(levelNamed(loaded, "Advanced")));
        flushAndClear();
        Association reloaded = associations.findById(stored.id()).orElseThrow();

        // Assert
        assertThat(reloaded).usingRecursiveComparison().ignoringFields("version").isEqualTo(saved);
        assertThat(reloaded.version()).isEqualTo(1L);
        assertThat(storedVersion(reloaded)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from levels where association_id = ?", Integer.class,
                stored.id().value())).isEqualTo(4);
    }

    @Test
    void onlyAChildLevelChangingStillMovesTheAssociationVersion() {
        // Arrange
        Association stored = associations.save(association());
        flushAndClear();
        Association loaded = associations.findById(stored.id()).orElseThrow();

        // Act
        Association saved = associations.save(loaded.renameLevel(levelNamed(loaded, "Beginner"), "Newcomer"));
        flushAndClear();

        // Assert
        assertThat(saved.version()).isEqualTo(1L);
        assertThat(storedVersion(saved)).isEqualTo(1L);
        assertThat(associations.findById(stored.id()).orElseThrow().levels()).extracting(Level::name)
                .containsExactly("Newcomer", "Intermediate", "Advanced");
    }

    @Test
    void aChangedColumnMovesTheVersionExactlyOnce() {
        // Arrange
        Association stored = associations.save(association());
        flushAndClear();
        Association loaded = associations.findById(stored.id()).orElseThrow();

        // Act
        Association saved = associations.save(loaded.updateDetails("New name", null, "Faro", "x@y.co"));
        flushAndClear();

        // Assert
        assertThat(saved.version()).isEqualTo(1L);
        assertThat(storedVersion(saved)).isEqualTo(1L);
    }

    @Test
    void aStaleCopyIsRejectedAndTheOtherEditIsNotLost() {
        // Arrange
        Association stored = associations.save(association());
        flushAndClear();
        Association copyA = associations.findById(stored.id()).orElseThrow();
        Association copyB = associations.findById(stored.id()).orElseThrow();
        associations.save(copyB.updateDetails("Renamed by B", null, "Faro", "b@b.co"));
        flushAndClear();
        Executable act = () -> associations.save(copyA.updateDetails("Renamed by A", null, "Faro", "a@a.co"));

        // Act
        AggregateModifiedConcurrentlyException ex = assertThrows(AggregateModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(ex.aggregateId()).isEqualTo(stored.id().value());
        assertThat(associations.findById(stored.id()).orElseThrow().name()).isEqualTo("Renamed by B");
    }

    @Test
    void shortNameLookupsFindTheRightAssociationAmongSeveral() {
        // Arrange
        Association first = associations.save(association("first-club"));
        Association second = associations.save(association("second-club"));
        flushAndClear();

        // Act
        Association foundFirst = associations.findByShortName(ShortName.of("first-club")).orElseThrow();
        Association foundSecond = associations.findByShortName(ShortName.of("second-club")).orElseThrow();

        // Assert
        assertThat(foundFirst.id()).isEqualTo(first.id());
        assertThat(foundSecond.id()).isEqualTo(second.id());
        assertThat(foundFirst.levels()).extracting(Level::id).doesNotContainAnyElementsOf(
                foundSecond.levels().stream().map(Level::id).toList());
        assertThat(associations.existsByShortName(ShortName.of("first-club"))).isTrue();
        assertThat(associations.existsByShortName(ShortName.of("third-club"))).isFalse();
        assertThat(associations.findByShortName(ShortName.of("third-club"))).isEmpty();
    }

    @Test
    void eachAssociationOnlyEverSeesItsOwnLevels() {
        // Arrange
        Association a = associations.save(association());
        Association b = associations.save(association());
        flushAndClear();

        // Act
        Association loadedA = associations.findById(a.id()).orElseThrow();
        Association loadedB = associations.findById(b.id()).orElseThrow();

        // Assert
        assertThat(loadedA.levels()).allMatch(level -> level.associationId().equals(a.id()));
        assertThat(loadedB.levels()).allMatch(level -> level.associationId().equals(b.id()));
        assertThat(loadedA.levels()).hasSize(3);
        assertThat(jdbc.queryForObject("select count(*) from levels where association_id in (?, ?)", Integer.class,
                a.id().value(), b.id().value())).isEqualTo(6);
    }

    @Test
    void aDuplicateShortNameIsRejectedAsShortNameAlreadyTaken() {
        // Arrange
        associations.save(association("taken-name"));
        flushAndClear();
        Executable act = () -> associations.save(association("taken-name"));

        // Act
        ShortNameAlreadyTakenException ex = assertThrows(ShortNameAlreadyTakenException.class, act);

        // Assert
        assertThat(ex.shortName()).isEqualTo(ShortName.of("taken-name"));
    }

    @Test
    void anUnknownAssociationIsNotFound() {
        // Arrange
        Association neverSaved = association();

        // Act
        boolean found = associations.findById(neverSaved.id()).isPresent();

        // Assert
        assertThat(found).isFalse();
    }

    @Test
    void findByIdForUpdateReturnsTheStoredAssociationWithItsLevels() {
        // Arrange
        Association stored = associations.save(Fixtures.association());
        entityManager.flush();
        entityManager.clear();

        // Act
        var locked = associations.findByIdForUpdate(stored.id());
        var unknown = associations.findByIdForUpdate(com.regivolley.api.domain.model.valueobject.AssociationId.generate());

        // Assert
        assertThat(locked).hasValueSatisfying(found -> {
            assertThat(found.id()).isEqualTo(stored.id());
            assertThat(found.levels()).hasSize(3);
        });
        assertThat(unknown).isEmpty();
    }
}
