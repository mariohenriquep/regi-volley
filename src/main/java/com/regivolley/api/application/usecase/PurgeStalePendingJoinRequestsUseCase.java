package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.PurgeStalePendingJoinRequestsCommand;
import com.regivolley.api.application.result.JoinRequestPurgeReport;

/** RGPD: the daily erasure of join requests left undecided for 30 days. */
public interface PurgeStalePendingJoinRequestsUseCase extends UseCase<PurgeStalePendingJoinRequestsCommand, JoinRequestPurgeReport> {
}
