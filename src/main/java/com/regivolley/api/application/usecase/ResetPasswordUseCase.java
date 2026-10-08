package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ResetPasswordCommand;

/** Forgotten password: consumes the emailed reset link and sets a new password (threat model D-11a). */
public interface ResetPasswordUseCase extends UseCase<ResetPasswordCommand, Void> {
}
