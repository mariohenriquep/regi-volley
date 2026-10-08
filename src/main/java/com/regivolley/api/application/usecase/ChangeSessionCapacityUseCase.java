package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ChangeSessionCapacityCommand;
import com.regivolley.api.application.result.CapacityChanged;

/** US-12: change a session's capacity; raising it promotes the waitlist. */
public interface ChangeSessionCapacityUseCase extends UseCase<ChangeSessionCapacityCommand, CapacityChanged> {
}
