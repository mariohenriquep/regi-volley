package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ResendActivationLinkCommand;

/**
 * An administrator re-sends a member's activation link (threat model P7). The call returns normally whether or not a link was sent,
 * so the answer says nothing about the member's activation state.
 */
public interface ResendActivationLinkUseCase extends UseCase<ResendActivationLinkCommand, Void> {
}
