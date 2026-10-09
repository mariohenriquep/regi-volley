package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetSessionRosterQuery;
import com.regivolley.api.application.result.SessionRoster;

/** US-17: the coach (or an administrator) reads who is in a session, with the booking ids needed to mark attendance. */
public interface GetSessionRosterUseCase extends UseCase<GetSessionRosterQuery, SessionRoster> {
}
