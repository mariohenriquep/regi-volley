package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CreatePlanCommand;
import com.regivolley.api.domain.model.entity.Plan;

/** US-19: an administrator creates a plan. */
public interface CreatePlanUseCase extends UseCase<CreatePlanCommand, Plan> {
}
