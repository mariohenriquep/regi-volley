package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionPaymentsQuery;
import com.regivolley.api.application.result.SubscriptionPayments;

/** US-21, RN-17, RN-19: an administrator reads the payments and reversals of one subscription. */
public interface ListSubscriptionPaymentsUseCase extends UseCase<ListSubscriptionPaymentsQuery, SubscriptionPayments> {
}
