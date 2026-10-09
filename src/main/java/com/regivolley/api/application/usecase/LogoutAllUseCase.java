package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.LogoutAllCommand;

/** Ends every login of the account and invalidates its access tokens at once. */
public interface LogoutAllUseCase extends UseCase<LogoutAllCommand, Void> {
}
