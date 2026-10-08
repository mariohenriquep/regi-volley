package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ReorderLevelsCommand;
import com.regivolley.api.domain.model.entity.Association;

/** US-03: an administrator reorders the levels. */
public interface ReorderLevelsUseCase extends UseCase<ReorderLevelsCommand, Association> {
}
