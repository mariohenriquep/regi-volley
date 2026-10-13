package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListLevelsQuery;
import com.regivolley.api.domain.model.entity.Association;

/** US-03: an administrator reads the association's levels (the association, which orders them and names the entry level). */
public interface ListLevelsUseCase extends UseCase<ListLevelsQuery, Association> {
}
