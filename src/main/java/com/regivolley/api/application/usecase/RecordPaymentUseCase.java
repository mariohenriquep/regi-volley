package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.RecordPaymentCommand;
import com.regivolley.api.application.result.PaymentRecorded;

/** US-21, RN-17: an administrator records a payment for a subscription. */
public interface RecordPaymentUseCase extends UseCase<RecordPaymentCommand, PaymentRecorded> {
}
