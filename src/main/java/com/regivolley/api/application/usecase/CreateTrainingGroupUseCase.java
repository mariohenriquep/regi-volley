package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreateTrainingGroupCommand;
import com.regivolley.api.domain.model.entity.TrainingGroup;

/** US-09: an administrator creates a training group. */
public interface CreateTrainingGroupUseCase extends UseCase<CreateTrainingGroupCommand, TrainingGroup> {
}
