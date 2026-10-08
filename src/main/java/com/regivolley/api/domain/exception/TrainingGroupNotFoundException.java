package com.regivolley.api.domain.exception;

import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

/** Thrown when a training group id doesn't exist in the association it is addressed to (maps to "not found"). */
public class TrainingGroupNotFoundException extends RuntimeException {

    private final TrainingGroupId trainingGroupId;

    public TrainingGroupNotFoundException(TrainingGroupId trainingGroupId) {
        super("Training group not found in this association: " + trainingGroupId);
        this.trainingGroupId = trainingGroupId;
    }

    public TrainingGroupId trainingGroupId() {
        return trainingGroupId;
    }
}
