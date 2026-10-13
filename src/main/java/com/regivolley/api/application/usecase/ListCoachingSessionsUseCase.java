package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListCoachingSessionsQuery;
import com.regivolley.api.application.result.CoachingSession;

import java.util.List;

/** The staff's week: the sessions a coach coaches (an administrator: all of them) with their counts and the actions open now. */
public interface ListCoachingSessionsUseCase extends UseCase<ListCoachingSessionsQuery, List<CoachingSession>> {
}
