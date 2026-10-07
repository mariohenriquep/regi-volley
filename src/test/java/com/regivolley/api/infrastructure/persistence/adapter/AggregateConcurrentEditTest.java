package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.exception.AggregateModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.infrastructure.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent admin edits of a training group or an association must not silently overwrite each
 * other (architecture.md section 10): two real, committed transactions load the same version and
 * both save; exactly one is stored.
 */
@SpringBootTest
class AggregateConcurrentEditTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TrainingGroupRepository groups;
    @Autowired
    private AssociationRepository associations;

    @Test
    void twoConcurrentEditsOfATrainingGroupStoreExactlyOne() throws Exception {
        for (int round = 0; round < 10; round++) {
            // Arrange
            Association association = associations.save(Fixtures.association());
            TrainingGroup stored = groups.save(Fixtures.group(association.id(), "Open play",
                    Set.of(association.levels().get(0).id()), MemberId.generate()));

            // Act
            List<Throwable> failures = Races.race(
                    () -> groups.findById(association.id(), stored.id()).orElseThrow(),
                    List.of(group -> groups.save(group.rename("Edited by A")),
                            group -> groups.save(group.archive())));

            // Assert
            assertThat(failures).hasSize(1).allMatch(AggregateModifiedConcurrentlyException.class::isInstance);
            assertThat(groups.findById(association.id(), stored.id()).orElseThrow().version()).isEqualTo(1L);
        }
    }

    @Test
    void twoConcurrentEditsOfAnAssociationStoreExactlyOne() throws Exception {
        for (int round = 0; round < 10; round++) {
            // Arrange
            Association stored = associations.save(Fixtures.association());

            // Act
            List<Throwable> failures = Races.race(
                    () -> associations.findById(stored.id()).orElseThrow(),
                    List.of(association -> associations.save(association.addLevel("Pro")),
                            association -> associations.save(association.updateDetails("Renamed", null, "Faro", "x@y.co"))));

            // Assert
            assertThat(failures).hasSize(1).allMatch(AggregateModifiedConcurrentlyException.class::isInstance);
            assertThat(associations.findById(stored.id()).orElseThrow().version()).isEqualTo(1L);
        }
    }
}
