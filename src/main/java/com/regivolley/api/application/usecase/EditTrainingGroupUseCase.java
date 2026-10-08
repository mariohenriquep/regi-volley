package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.EditTrainingGroupCommand;
import com.regivolley.api.domain.model.entity.TrainingGroup;

/** US-09: an administrator edits a training group. */
public interface EditTrainingGroupUseCase extends UseCase<EditTrainingGroupCommand, TrainingGroup> {
}
