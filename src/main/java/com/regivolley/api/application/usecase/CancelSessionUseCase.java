package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CancelSessionCommand;
import com.regivolley.api.application.result.CancelledSession;

/** US-11: cancel a session with a reason; credits are refunded and members notified. */
public interface CancelSessionUseCase extends UseCase<CancelSessionCommand, CancelledSession> {
}
