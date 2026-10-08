package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RejectJoinRequestCommand;
import com.regivolley.api.domain.model.entity.JoinRequest;

/** US-06: an administrator rejects a join request. */
public interface RejectJoinRequestUseCase extends UseCase<RejectJoinRequestCommand, JoinRequest> {
}
