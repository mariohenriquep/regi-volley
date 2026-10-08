package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MarkSubscriptionOverdueCommand;
import com.regivolley.api.domain.model.entity.Subscription;

/** RN-18: an administrator marks a pending subscription as overdue. */
public interface MarkSubscriptionOverdueUseCase extends UseCase<MarkSubscriptionOverdueCommand, Subscription> {
}
