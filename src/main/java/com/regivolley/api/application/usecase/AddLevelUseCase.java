package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AddLevelCommand;
import com.regivolley.api.domain.model.entity.Association;

/** US-03: an administrator adds a level. */
public interface AddLevelUseCase extends UseCase<AddLevelCommand, Association> {
}
