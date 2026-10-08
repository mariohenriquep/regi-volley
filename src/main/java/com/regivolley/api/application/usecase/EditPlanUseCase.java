package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.EditPlanCommand;
import com.regivolley.api.domain.model.entity.Plan;

/** US-19: an administrator edits a plan. */
public interface EditPlanUseCase extends UseCase<EditPlanCommand, Plan> {
}
