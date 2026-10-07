package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.PlanModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.PlanId;

import java.util.Optional;

/** Port for {@link Plan} aggregates. Every read is scoped to one association (architecture.md section 8). */
public interface PlanRepository {

    Optional<Plan> findById(AssociationId associationId, PlanId id);

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
