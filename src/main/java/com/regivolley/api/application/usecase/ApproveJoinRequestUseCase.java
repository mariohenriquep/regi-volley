package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ApproveJoinRequestCommand;
import com.regivolley.api.domain.model.result.JoinRequestApproval;

/** US-06: an administrator approves a join request; the member starts at the entry level. */
public interface ApproveJoinRequestUseCase extends UseCase<ApproveJoinRequestCommand, JoinRequestApproval> {
}
