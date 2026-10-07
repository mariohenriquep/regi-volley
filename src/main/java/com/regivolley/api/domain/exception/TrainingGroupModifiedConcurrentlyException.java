package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

/**
 * A training group was changed by a concurrent request between load and save (two admin edits, or an
 * archive racing a session generation run), so this write was rejected and nothing was stored.
 */
public class TrainingGroupModifiedConcurrentlyException extends AggregateModifiedConcurrentlyException {

    private final TrainingGroupId trainingGroupId;

    public TrainingGroupModifiedConcurrentlyException(TrainingGroupId trainingGroupId) {
        super("training group", trainingGroupId.value());
        this.trainingGroupId = trainingGroupId;
    }

    public TrainingGroupId trainingGroupId() {
        return trainingGroupId;
    }
}
