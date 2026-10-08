package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.PlanId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port for {@link Plan} aggregates. Every read is scoped to one association (architecture.md section 8). */
public interface PlanRepository {

    Optional<Plan> findById(AssociationId associationId, PlanId id);

    /** The plans of this association among these ids (unknown or foreign ids are simply absent), in no particular order. */
    List<Plan> findByIds(AssociationId associationId, Collection<PlanId> ids);

    /**
     * Inserts a new plan or updates an existing one, and returns it as stored, with its new version (each
     * save moves it forward by one); keep working with the returned instance.
     *
     * @throws PlanModifiedConcurrentlyException if the stored plan is no longer at the version of {@code plan},
     *         if it has a version but no stored row in its association, or if its row could not be locked in
     *         time; nothing is written. Retry in a new transaction.
     */
    Plan save(Plan plan);
}
