package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@PersistenceTest
class PlanRepositoryAdapterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PlanRepository plans;
    @Autowired
    private AssociationRepository associations;
    @Autowired
    private EntityManager entityManager;

    private Association newAssociation() {
        return associations.save(Fixtures.association());
    }

    private Plan saveAndReload(Plan plan) {
        Plan saved = plans.save(plan);
        entityManager.flush();
        entityManager.clear();
        return plans.findById(saved.associationId(), saved.id()).orElseThrow();
    }

    @Test
    void aPackWithLevelsAndValidityRoundTrips() {
        // Arrange
        Association association = newAssociation();
        Set<LevelId> levels = Set.of(association.levels().get(0).id(), association.levels().get(1).id());
        Plan plan = Fixtures.pack(association.id(), levels);

        // Act
        Plan loaded = saveAndReload(plan);

        // Assert
        assertThat(loaded).usingRecursiveComparison().isEqualTo(plan);
        assertThat(loaded.allowedLevels()).isEqualTo(levels);
        assertThat(loaded.price()).isEqualTo(Money.ofCents(4500));
        assertThat(loaded.validityDays()).hasValue(90);
    }

    @Test
    void everyPlanTypeRoundTrips() {
        // Arrange
        Association association = newAssociation();
        Plan unlimited = Plan.create(association.id(), "Unlimited", PlanTerms.monthlyUnlimited(Set.of()), Money.ofCents(5000), null);
        Plan weekly = Fixtures.monthlyNPerWeek(association.id());
        Plan single = Plan.create(association.id(), "Drop-in", PlanTerms.singleSession(Set.of()), Money.ofCents(700), 1);

        // Act
        Plan loadedUnlimited = saveAndReload(unlimited);
        Plan loadedWeekly = saveAndReload(weekly);
        Plan loadedSingle = saveAndReload(single);

        // Assert
        assertThat(loadedUnlimited).usingRecursiveComparison().isEqualTo(unlimited);
        assertThat(loadedWeekly).usingRecursiveComparison().isEqualTo(weekly);
        assertThat(loadedSingle).usingRecursiveComparison().isEqualTo(single);
        assertThat(loadedUnlimited.type()).isEqualTo(PlanType.MONTHLY_UNLIMITED);
        assertThat(loadedUnlimited.validityDays()).isEmpty();
        assertThat(loadedWeekly.terms().sessionsPerWeek()).isEqualTo(2);
    }

    @Test
    void savingAgainUpdatesThePlanInPlace() {
        // Arrange
        Association association = newAssociation();
        Plan plan = plans.save(Fixtures.pack(association.id(), Set.of(association.levels().get(0).id())));
        entityManager.flush();
        entityManager.clear();
        Plan changed = Plan.reconstruct(plan.id(), plan.associationId(), "Renamed pack",
                PlanTerms.pack(10, Set.of(association.levels().get(2).id())), Money.ofCents(5000), 60, plan.version());

        // Act
        Plan loaded = saveAndReload(changed);

        // Assert
        assertThat(loaded).usingRecursiveComparison().ignoringFields("version").isEqualTo(changed);
        assertThat(loaded.version()).isEqualTo(1L);
        assertThat(loaded.allowedLevels()).containsExactly(association.levels().get(2).id());
    }

    @Test
    void aPlanIsInvisibleToAnotherAssociation() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Plan ofA = plans.save(Fixtures.pack(a.id(), Set.of()));
        entityManager.flush();
        entityManager.clear();

        // Act
        boolean visibleToB = plans.findById(b.id(), ofA.id()).isPresent();

        // Assert
        assertThat(visibleToB).isFalse();
        assertThat(plans.findById(a.id(), ofA.id())).isPresent();
    }

    @Test
    void everySaveMovesTheVersionByExactlyOneAndAStaleCopyIsRejected() {
        // Arrange
        Association association = newAssociation();
        Plan stored = plans.save(Fixtures.pack(association.id(), Set.of()));
        entityManager.flush();
        entityManager.clear();
        Plan copyA = plans.findById(association.id(), stored.id()).orElseThrow();
        Plan copyB = plans.findById(association.id(), stored.id()).orElseThrow();
        Plan renamed = plans.save(Plan.reconstruct(copyB.id(), copyB.associationId(), "Renamed", copyB.terms(),
                copyB.price(), 90, copyB.version()));
        entityManager.flush();
        entityManager.clear();
        Executable act = () -> plans.save(Plan.reconstruct(copyA.id(), copyA.associationId(), "Stale rename",
                copyA.terms(), copyA.price(), 90, copyA.version()));

        // Act
        PlanModifiedConcurrentlyException ex = assertThrows(PlanModifiedConcurrentlyException.class, act);

        // Assert
        assertThat(stored.version()).isZero();
        assertThat(renamed.version()).isEqualTo(1L);
        assertThat(ex.planId()).isEqualTo(stored.id());
        assertThat(plans.findById(association.id(), stored.id()).orElseThrow().name()).isEqualTo("Renamed");
    }

    @Test
    void findByIdsReturnsTheRequestedPlansOfThatAssociationOnly() {
        // Arrange
        Association a = newAssociation();
        Association b = newAssociation();
        Plan one = plans.save(Fixtures.pack(a.id(), Set.of()));
        Plan two = plans.save(Fixtures.monthlyNPerWeek(a.id()));
        plans.save(Fixtures.pack(a.id(), Set.of()));
        Plan foreign = plans.save(Fixtures.pack(b.id(), Set.of()));
        entityManager.flush();
        entityManager.clear();

        // Act
        var found = plans.findByIds(a.id(), java.util.List.of(one.id(), two.id(), foreign.id()));

        // Assert
        assertThat(found).extracting(Plan::id).containsExactlyInAnyOrder(one.id(), two.id());
        assertThat(plans.findByIds(b.id(), java.util.List.of(one.id()))).isEmpty();
        assertThat(plans.findByIds(a.id(), java.util.List.of())).isEmpty();
    }
}
