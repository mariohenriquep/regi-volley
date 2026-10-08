package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ReversePaymentCommand;
import com.regivolley.api.application.result.PaymentReversed;

/** RN-19: an administrator reverses a payment. */
public interface ReversePaymentUseCase extends UseCase<ReversePaymentCommand, PaymentReversed> {
}
