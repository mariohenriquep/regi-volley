package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.SubmitJoinRequestCommand;
import com.regivolley.api.application.result.JoinRequestSubmitted;

/** US-05: a visitor asks to join an association. */
public interface SubmitJoinRequestUseCase extends UseCase<SubmitJoinRequestCommand, JoinRequestSubmitted> {
}
