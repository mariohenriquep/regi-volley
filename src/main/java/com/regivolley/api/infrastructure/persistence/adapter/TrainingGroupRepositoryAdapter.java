package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.infrastructure.persistence.entity.TrainingGroupJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.TrainingGroupPersistenceMapper;
import jakarta.persistence.EntityManager;
import com.regivolley.api.domain.exception.TrainingGroupModifiedConcurrentlyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** {@link TrainingGroupRepository} on Spring Data JPA; every save of an existing group moves its version forward by one. */
@Component
public class TrainingGroupRepositoryAdapter implements TrainingGroupRepository {

    private final TrainingGroupJpaRepository groups;
    private final EntityManager entityManager;

    public TrainingGroupRepositoryAdapter(TrainingGroupJpaRepository groups, EntityManager entityManager) {
        this.groups = groups;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TrainingGroup> findById(AssociationId associationId, TrainingGroupId id) {
        return groups.findByIdAndAssociationId(id.value(), associationId.value())
                .map(TrainingGroupPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingGroup> findAllByAssociation(AssociationId associationId) {
        return groups.findAllByAssociationIdOrderByNameAscIdAsc(associationId.value()).stream()
                .map(TrainingGroupPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveWithVenue(AssociationId associationId, VenueId venueId) {
        return groups.existsByAssociationIdAndVenueIdAndStatus(associationId.value(), venueId.value(),
                TrainingGroupStatus.ACTIVE.name());
    }

    @Override
    @Transactional
    public TrainingGroup save(TrainingGroup group) {
        return WriteSupport.translatingConflicts(() -> TrainingGroupPersistenceMapper.toDomain(
                WriteSupport.write(groups, entityManager,
                        groups.findForUpdateByIdAndAssociationId(group.id().value(), group.associationId().value()),
                        group.version(), TrainingGroupJpaEntity::new,
                        entity -> TrainingGroupPersistenceMapper.apply(group, entity),
                        TrainingGroupJpaEntity::getVersion, () -> conflict(group))),
                () -> conflict(group));
    }

    private static TrainingGroupModifiedConcurrentlyException conflict(TrainingGroup group) {
        return new TrainingGroupModifiedConcurrentlyException(group.id());
    }
}
