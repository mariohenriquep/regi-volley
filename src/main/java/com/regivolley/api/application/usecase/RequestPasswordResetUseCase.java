package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RequestPasswordResetCommand;

/** "I forgot my password": the answer is the same whether or not the address has an account (threat model D-10). */
public interface RequestPasswordResetUseCase extends UseCase<RequestPasswordResetCommand, Void> {
}
