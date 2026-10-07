package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GenerateSessionsCommand;
import com.regivolley.api.application.result.SessionGenerationReport;

/** US-10: generate the coming weeks' sessions of one association. */
public interface GenerateSessionsUseCase extends UseCase<GenerateSessionsCommand, SessionGenerationReport> {
}
