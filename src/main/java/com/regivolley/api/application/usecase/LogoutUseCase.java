package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LogoutCommand;

/** Ends one login (the family of the presented refresh token). */
public interface LogoutUseCase extends UseCase<LogoutCommand, Void> {
}
