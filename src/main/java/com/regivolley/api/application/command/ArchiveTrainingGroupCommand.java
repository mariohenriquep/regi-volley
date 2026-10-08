package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.util.Objects;

/** An administrator retires a training group (US-09): it generates no more sessions. */
public record ArchiveTrainingGroupCommand(Actor actor, TrainingGroupId groupId) {

    public ArchiveTrainingGroupCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(groupId, "groupId must not be null");
    }
}
