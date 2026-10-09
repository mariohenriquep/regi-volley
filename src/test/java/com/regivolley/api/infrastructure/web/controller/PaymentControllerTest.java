package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.ReversePaymentCommand;
import com.regivolley.api.application.result.PaymentReversed;
import com.regivolley.api.domain.factory.PaymentFactory;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RN-19 over HTTP: a payment is reversed by a new, negative one. */
class PaymentControllerTest extends AbstractControllerWebTest {

    private Subscription subscription;

    @BeforeEach
    void setUpSubscription() {
        Association other = WebFixtures.association();
        Member debtor = WebFixtures.member(other);
        subscription = WebFixtures.subscription(WebFixtures.pack(other), debtor.id());
    }

    @Test
    void reversingAPaymentAnswersWithTheReversalAsANewPayment() throws Exception {
        // Arrange
        Payment payment = WebFixtures.payment(subscription, member.id());
        Payment reversal = PaymentFactory.createReversal(payment, member.id(), WebFixtures.CLOCK);
        when(reversePaymentUseCase.execute(any())).thenReturn(new PaymentReversed(reversal, subscription, Money.ofCents(4500)));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/payments/" + payment.id() + "/reversal");

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversal.reversalOf").value(payment.id().value().toString()))
                .andExpect(jsonPath("$.reversal.amountCents").value(1500))
                .andExpect(jsonPath("$.outstandingCents").value(4500));
        ArgumentCaptor<ReversePaymentCommand> command = ArgumentCaptor.forClass(ReversePaymentCommand.class);
        verify(reversePaymentUseCase).execute(command.capture());
        assertThat(command.getValue().paymentId()).isEqualTo(payment.id());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }
}
