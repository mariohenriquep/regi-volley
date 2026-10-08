package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeEntryLevelCommand;
import com.regivolley.api.domain.model.entity.Association;

/** US-03, RN-20: an administrator chooses the entry level. */
public interface ChangeEntryLevelUseCase extends UseCase<ChangeEntryLevelCommand, Association> {
}
