package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LoginCommand;
import com.regivolley.api.application.result.SessionTokens;

/** Email and password to a session (threat model D-8, D-10). */
public interface LoginUseCase extends UseCase<LoginCommand, SessionTokens> {
}
