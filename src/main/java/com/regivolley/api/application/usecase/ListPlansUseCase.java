package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListPlansQuery;
import com.regivolley.api.application.result.PlanListEntry;

import java.util.List;

/** US-19: an administrator lists the association's plans. */
public interface ListPlansUseCase extends UseCase<ListPlansQuery, List<PlanListEntry>> {
}
