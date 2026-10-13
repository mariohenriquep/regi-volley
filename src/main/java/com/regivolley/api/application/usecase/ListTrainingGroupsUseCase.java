package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListTrainingGroupsQuery;
import com.regivolley.api.application.result.TrainingGroupListEntry;

import java.util.List;

/** US-09: an administrator lists the association's training groups. */
public interface ListTrainingGroupsUseCase extends UseCase<ListTrainingGroupsQuery, List<TrainingGroupListEntry>> {
}
