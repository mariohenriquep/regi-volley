package com.regivolley.api.application.command;

/** Erases the join requests that nobody decided for 30 days, in every association; the daily job (RGPD data minimisation). */
public record PurgeStalePendingJoinRequestsCommand() {
}
