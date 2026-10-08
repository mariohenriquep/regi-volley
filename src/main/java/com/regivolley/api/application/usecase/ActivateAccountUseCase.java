package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ActivateAccountCommand;

/** First sign-in: consumes the emailed activation link, sets the password and confirms the membership (threat model D-11). */
public interface ActivateAccountUseCase extends UseCase<ActivateAccountCommand, Void> {
}
