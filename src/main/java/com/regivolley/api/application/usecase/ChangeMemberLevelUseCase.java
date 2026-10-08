package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeMemberLevelCommand;
import com.regivolley.api.domain.model.entity.Member;

/** US-07, RN-20: a coach or an administrator moves a member to another level. */
public interface ChangeMemberLevelUseCase extends UseCase<ChangeMemberLevelCommand, Member> {
}
