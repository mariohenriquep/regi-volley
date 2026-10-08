package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ArchiveTrainingGroupCommand;
import com.regivolley.api.domain.model.entity.TrainingGroup;

/** US-09: an administrator archives a training group. */
public interface ArchiveTrainingGroupUseCase extends UseCase<ArchiveTrainingGroupCommand, TrainingGroup> {
}
