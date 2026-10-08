package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AssignPlanCommand;
import com.regivolley.api.domain.model.entity.Subscription;

/** US-20, RN-16: an administrator gives a member a plan as a new subscription. */
public interface AssignPlanUseCase extends UseCase<AssignPlanCommand, Subscription> {
}
