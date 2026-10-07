package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

/** Thrown when an archived training group is edited or archived again (US-09). */
public class TrainingGroupArchivedException extends BusinessRuleException {

    private final TrainingGroupId trainingGroupId;

    public TrainingGroupArchivedException(TrainingGroupId trainingGroupId) {
        super("The training group is archived and can no longer be changed");
        this.trainingGroupId = trainingGroupId;
    }

    public TrainingGroupId trainingGroupId() {
        return trainingGroupId;
    }
}
