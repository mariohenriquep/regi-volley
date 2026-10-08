package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.result.SubscriptionPaymentEntry;

import java.util.List;

/** US-22: an administrator lists subscriptions by payment status. */
public interface ListSubscriptionsByPaymentStatusUseCase extends UseCase<ListSubscriptionsByPaymentStatusQuery, List<SubscriptionPaymentEntry>> {
}
