package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RevokeRoleCommand;
import com.regivolley.api.domain.model.entity.Member;

/** An administrator takes a role away from a member; the last active administrator cannot lose ADMIN. */
public interface RevokeRoleUseCase extends UseCase<RevokeRoleCommand, Member> {
}
