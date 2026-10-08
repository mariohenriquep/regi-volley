package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GenerateSessionsForAllAssociationsCommand;
import com.regivolley.api.application.result.GenerationRunReport;

/** US-10: the daily run of the session generation over every association. */
public interface GenerateSessionsForAllAssociationsUseCase
        extends UseCase<GenerateSessionsForAllAssociationsCommand, GenerationRunReport> {
}
