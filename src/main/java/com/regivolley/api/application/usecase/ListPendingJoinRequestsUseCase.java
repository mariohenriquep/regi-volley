package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListPendingJoinRequestsQuery;
import com.regivolley.api.domain.model.entity.JoinRequest;

import java.util.List;

/** US-06: an administrator lists the join requests waiting for a decision. */
public interface ListPendingJoinRequestsUseCase extends UseCase<ListPendingJoinRequestsQuery, List<JoinRequest>> {
}
