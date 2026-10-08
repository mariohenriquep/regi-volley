package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RenameLevelCommand;
import com.regivolley.api.domain.model.entity.Association;

/** US-03: an administrator renames a level. */
public interface RenameLevelUseCase extends UseCase<RenameLevelCommand, Association> {
}
