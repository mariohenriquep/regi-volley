package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.infrastructure.persistence.adapter.Fixtures;
import com.regivolley.api.infrastructure.persistence.entity.AssociationJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.JoinRequestJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.LevelJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.MemberJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.PlanJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.TrainingGroupJpaEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The version is owned by the persistence layer: a mapper never copies it onto an entity, and an
 * entity that has not been stored yet (no version) maps to a domain aggregate at version 0.
 */
class AggregateVersionMappingTest {

    @Test
    void anAssociationEntityWithoutAVersionMapsToVersionZeroAndAppliesNone() {
        // Arrange
        Association association = Fixtures.association();
        AssociationJpaEntity entity = new AssociationJpaEntity();

        // Act
        AssociationPersistenceMapper.apply(association, entity);
        Association mapped = AssociationPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(entity.getVersion()).isNull();
        assertThat(mapped.version()).isZero();
        assertThat(mapped).usingRecursiveComparison().isEqualTo(association);
    }

    @Test
    void anAssociationEntityIsSyncedLevelByLevel() {
        // Arrange
        Association association = Fixtures.association();
        AssociationJpaEntity entity = new AssociationJpaEntity();
        AssociationPersistenceMapper.apply(association, entity);
        LevelJpaEntity beginnerRow = entity.getLevels().get(0);
        Association renamedAndExtended = association
                .renameLevel(association.levels().get(0).id(), "Newcomer").addLevel("Pro");
        Association withoutPro = Association.reconstruct(association.id(), association.name(), association.shortName(),
                null, association.locality(), association.contactEmail(), association.bookingPolicy(),
                association.sessionGenerationPolicy(), association.levels().subList(0, 2),
                association.levels().get(0).id(), 0L);

        // Act
        AssociationPersistenceMapper.apply(renamedAndExtended, entity);
        int afterExtending = entity.getLevels().size();
        AssociationPersistenceMapper.apply(withoutPro, entity);

        // Assert
        assertThat(afterExtending).isEqualTo(4);
        assertThat(entity.getLevels()).hasSize(2);
        assertThat(entity.getLevels().get(0)).isSameAs(beginnerRow);
        assertThat(entity.getNif()).isNull();
        assertThat(entity.getLevels()).extracting(LevelJpaEntity::getLevelRank).containsExactly(0, 1);
        assertThat(association.levels()).extracting(Level::name).startsWith("Beginner");
    }

    @Test
    void aTrainingGroupEntityWithoutAVersionMapsToVersionZero() {
        // Arrange
        Association association = Fixtures.association();
        TrainingGroup group = Fixtures.group(association.id(), "Open play", Set.of(association.entryLevelId()), MemberId.generate());
        TrainingGroupJpaEntity entity = new TrainingGroupJpaEntity();

        // Act
        TrainingGroupPersistenceMapper.apply(group, entity);
        TrainingGroup mapped = TrainingGroupPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(entity.getVersion()).isNull();
        assertThat(mapped.version()).isZero();
        assertThat(mapped).usingRecursiveComparison().isEqualTo(group);
    }

    @Test
    void aSubscriptionEntityWithoutAVersionMapsToVersionZero() {
        // Arrange
        Association association = Fixtures.association();
        Subscription subscription = Fixtures.subscription(Fixtures.pack(association.id(), Set.of()), MemberId.generate(), "2026-10-01");
        SubscriptionJpaEntity entity = new SubscriptionJpaEntity();

        // Act
        SubscriptionPersistenceMapper.apply(subscription, entity);
        Subscription mapped = SubscriptionPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(entity.getVersion()).isNull();
        assertThat(mapped.version()).isZero();
        assertThat(mapped).usingRecursiveComparison().isEqualTo(subscription);
        assertThat(List.of(mapped)).hasSize(1);
    }

    @Test
    void aMemberEntityWithoutAVersionMapsToVersionZero() {
        // Arrange
        Association association = Fixtures.association();
        Member member = Fixtures.member(association, "Ana");
        MemberJpaEntity entity = new MemberJpaEntity();

        // Act
        MemberPersistenceMapper.apply(member, entity);
        Member mapped = MemberPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(entity.getVersion()).isNull();
        assertThat(mapped.version()).isZero();
        assertThat(mapped).usingRecursiveComparison().isEqualTo(member);
    }

    @Test
    void aJoinRequestEntityWithoutAVersionMapsToVersionZero() {
        // Arrange
        JoinRequest request = Fixtures.joinRequest(Fixtures.association().id(), "Rita", Fixtures.NOW);
        JoinRequestJpaEntity entity = new JoinRequestJpaEntity();

        // Act
        JoinRequestPersistenceMapper.apply(request, entity);
        JoinRequest mapped = JoinRequestPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(entity.getVersion()).isNull();
        assertThat(mapped.version()).isZero();
        assertThat(mapped).usingRecursiveComparison().isEqualTo(request);
    }

    @Test
    void aPlanEntityWithoutAVersionMapsToVersionZero() {
        // Arrange
        Plan plan = Fixtures.pack(Fixtures.association().id(), Set.of());
        PlanJpaEntity entity = new PlanJpaEntity();

        // Act
        PlanPersistenceMapper.apply(plan, entity);
        Plan mapped = PlanPersistenceMapper.toDomain(entity);

        // Assert
        assertThat(entity.getVersion()).isNull();
        assertThat(mapped.version()).isZero();
        assertThat(mapped).usingRecursiveComparison().isEqualTo(plan);
    }
}
