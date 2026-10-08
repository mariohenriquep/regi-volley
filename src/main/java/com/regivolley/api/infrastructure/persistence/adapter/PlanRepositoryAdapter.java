package com.regivolley.api.infrastructure.persistence.adapter;

import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.infrastructure.persistence.entity.PlanJpaEntity;
import com.regivolley.api.infrastructure.persistence.mapper.PlanPersistenceMapper;
import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** {@link PlanRepository} on Spring Data JPA. */
@Component
public class PlanRepositoryAdapter implements PlanRepository {

    private final PlanJpaRepository plans;
    private final EntityManager entityManager;

    public PlanRepositoryAdapter(PlanJpaRepository plans, EntityManager entityManager) {
        this.plans = plans;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Plan> findById(AssociationId associationId, PlanId id) {
        return plans.findByIdAndAssociationId(id.value(), associationId.value()).map(PlanPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Plan> findByIds(AssociationId associationId, Collection<PlanId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return plans.findByAssociationIdAndIdIn(associationId.value(), ids.stream().map(PlanId::value).toList())
                .stream().map(PlanPersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional
    public Plan save(Plan plan) {
        return WriteSupport.translatingConflicts(() -> PlanPersistenceMapper.toDomain(
                WriteSupport.write(plans, entityManager,
                        plans.findForUpdateByIdAndAssociationId(plan.id().value(), plan.associationId().value()),
                        plan.version(), PlanJpaEntity::new,
                        entity -> PlanPersistenceMapper.apply(plan, entity),
                        PlanJpaEntity::getVersion, () -> conflict(plan))),
                () -> conflict(plan));
    }

    private static PlanModifiedConcurrentlyException conflict(Plan plan) {
        return new PlanModifiedConcurrentlyException(plan.id());
    }
}
