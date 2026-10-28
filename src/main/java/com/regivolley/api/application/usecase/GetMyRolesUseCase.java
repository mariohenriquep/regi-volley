package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetMyRolesQuery;
import com.regivolley.api.application.result.MyRoles;

/** The caller learns which roles they hold, so a client can show what they may use; every operation is still authorised on its own. */
public interface GetMyRolesUseCase extends UseCase<GetMyRolesQuery, MyRoles> {
}
