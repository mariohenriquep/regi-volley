package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RefreshSessionCommand;
import com.regivolley.api.application.result.SessionTokens;

/** Rotates the presented refresh token into a new session (threat model D-7). */
public interface RefreshSessionUseCase extends UseCase<RefreshSessionCommand, SessionTokens> {
}
