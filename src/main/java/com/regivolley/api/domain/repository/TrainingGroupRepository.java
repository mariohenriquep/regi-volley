package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.TrainingGroupModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.util.List;
import java.util.Optional;

/** Port for {@link TrainingGroup} aggregates. Every read is scoped to one association (architecture.md section 8). */
public interface TrainingGroupRepository {

    Optional<TrainingGroup> findById(AssociationId associationId, TrainingGroupId id);

    /** All groups of the association, ACTIVE and ARCHIVED, by name. */
    List<TrainingGroup> findAllByAssociation(AssociationId associationId);

    /**
     * Inserts a new group or updates an existing one, and returns it as stored, with its new version
     * (each save moves it forward by one); keep working with the returned instance.
     *
     * @throws TrainingGroupModifiedConcurrentlyException if the stored group is no longer at the version of
     *         {@code group}, if it has a version but no stored row in its association, or if its row could
     *         not be locked in time; nothing is written. Retry in a new transaction.
     */
    TrainingGroup save(TrainingGroup group);
}
