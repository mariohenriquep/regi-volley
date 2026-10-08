package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GrantRoleCommand;
import com.regivolley.api.domain.model.entity.Member;

/** An administrator gives a member a role. */
public interface GrantRoleUseCase extends UseCase<GrantRoleCommand, Member> {
}
