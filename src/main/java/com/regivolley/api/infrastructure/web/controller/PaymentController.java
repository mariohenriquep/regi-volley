package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.ReversePaymentUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.PaymentReversedResponse;
import com.regivolley.api.infrastructure.web.mapper.SubscriptionWebMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Reversing a payment (US-21, RN-19): payments are append-only, so a reversal is a new payment that counts negatively. */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final ReversePaymentUseCase reversePayment;

    public PaymentController(ReversePaymentUseCase reversePayment) {
        this.reversePayment = reversePayment;
    }

    @PostMapping("/{paymentId}/reversal")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentReversedResponse reverse(@CurrentActor AuthenticatedActor caller, @PathVariable UUID paymentId) {
        return SubscriptionWebMapper.toResponse(reversePayment.execute(SubscriptionWebMapper.reversePaymentCommand(caller.actor(), paymentId)));
    }
}
