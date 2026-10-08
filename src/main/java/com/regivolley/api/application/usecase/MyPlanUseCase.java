package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MyPlanQuery;
import com.regivolley.api.application.result.MyPlan;

/** US-23: a member sees their plans, balance and payment status. */
public interface MyPlanUseCase extends UseCase<MyPlanQuery, MyPlan> {
}
