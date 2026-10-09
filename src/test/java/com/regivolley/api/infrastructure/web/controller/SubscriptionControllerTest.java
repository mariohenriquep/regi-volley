package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.command.MarkSubscriptionOverdueCommand;
import com.regivolley.api.application.command.RecordPaymentCommand;
import com.regivolley.api.application.result.PaymentRecorded;
import com.regivolley.api.application.result.SubscriptionPaymentEntry;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-21, US-22, RN-17, RN-18 over HTTP: who owes what, the CSV, and the money coming in. */
class SubscriptionControllerTest extends AbstractControllerWebTest {

    private Association other;
    private Member debtor;
    private Subscription subscription;

    @BeforeEach
    void setUpSubscription() {
        other = WebFixtures.association();
        debtor = WebFixtures.member(other);
        Plan plan = WebFixtures.pack(other);
        subscription = WebFixtures.subscription(plan, debtor.id());
    }

    @Test
    void theListByStatusShowsTheMembersNameAndThePriceInCents() throws Exception {
        // Arrange
        when(listSubscriptionsByPaymentStatusUseCase.execute(any())).thenReturn(List.of(new SubscriptionPaymentEntry(subscription.markOverdue(), "Ana Silva")));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=OVERDUE");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subscriptionId").value(subscription.id().value().toString()))
                .andExpect(jsonPath("$[0].memberId").value(debtor.id().value().toString()))
                .andExpect(jsonPath("$[0].memberName").value("Ana Silva"))
                .andExpect(jsonPath("$[0].paymentStatus").value("OVERDUE"))
                .andExpect(jsonPath("$[0].priceCents").value(4500));
        ArgumentCaptor<ListSubscriptionsByPaymentStatusQuery> query = ArgumentCaptor.forClass(ListSubscriptionsByPaymentStatusQuery.class);
        verify(listSubscriptionsByPaymentStatusUseCase).execute(query.capture());
        assertThat(query.getValue().status()).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void theWindowOnTheEndDateIsPassedToTheUseCaseForTheListAndTheCsvAlike() throws Exception {
        // Arrange
        when(listSubscriptionsByPaymentStatusUseCase.execute(any())).thenReturn(List.of());

        // Act
        authenticated(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=PENDING&endingFrom=2026-01-01&endingTo=2026-12-31");
        authenticated(HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=PAID&endingFrom=2026-02-01");
        authenticated(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=PAID");

        // Assert
        ArgumentCaptor<ListSubscriptionsByPaymentStatusQuery> query = ArgumentCaptor.forClass(ListSubscriptionsByPaymentStatusQuery.class);
        verify(listSubscriptionsByPaymentStatusUseCase, org.mockito.Mockito.times(3)).execute(query.capture());
        assertThat(query.getAllValues().get(0).endingFrom()).isEqualTo(LocalDate.parse("2026-01-01"));
        assertThat(query.getAllValues().get(0).endingTo()).isEqualTo(LocalDate.parse("2026-12-31"));
        assertThat(query.getAllValues().get(1).endingFrom()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(query.getAllValues().get(1).endingTo()).isNull();
        assertThat(query.getAllValues().get(2).endingFrom()).isNull();
    }

    @Test
    void aWindowDateOutsideTheYears2000To2100OrNotADateIs400() throws Exception {
        // Arrange
        // (the paths)

        // Act
        var early = authenticated(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=PAID&endingFrom=1999-12-31");
        var late = authenticated(HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=PAID&endingTo=2101-01-01");
        var junk = authenticated(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=PAID&endingTo=soon");

        // Assert
        early.andExpect(status().isBadRequest());
        late.andExpect(status().isBadRequest());
        junk.andExpect(status().isBadRequest());
        verifyNoInteractions(listSubscriptionsByPaymentStatusUseCase);
    }

    @Test
    void theStatusFilterIsRequiredAndMustBeKnown() throws Exception {
        // Arrange
        // (the requests)

        // Act
        var missing = authenticated(HttpMethod.GET, "/api/v1/subscriptions");
        var unknown = authenticated(HttpMethod.GET, "/api/v1/subscriptions?paymentStatus=LATE");
        var csvMissing = authenticated(HttpMethod.GET, "/api/v1/subscriptions/export");

        // Assert
        missing.andExpect(status().isBadRequest());
        unknown.andExpect(status().isBadRequest());
        csvMissing.andExpect(status().isBadRequest());
        verifyNoInteractions(listSubscriptionsByPaymentStatusUseCase);
    }

    @Test
    void theCsvIsADownloadWithUtf8TextCsvAndTheMembersAsRows() throws Exception {
        // Arrange
        when(listSubscriptionsByPaymentStatusUseCase.execute(any())).thenReturn(List.of(new SubscriptionPaymentEntry(subscription.markOverdue(), "Ana Silva")));

        // Act
        MvcResult result = authenticated(HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=OVERDUE").andReturn();

        // Assert
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(result.getResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"subscriptions-overdue.csv\"");
        assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        String csv = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿subscription_id,member_id,member_name,plan_type,start_date,end_date,payment_status,price_eur\r\n");
        assertThat(csv).contains(subscription.id().value() + "," + debtor.id().value() + ",Ana Silva,PACK,2026-10-12,2027-01-09,OVERDUE,45.00\r\n");
    }

    @Test
    void aMemberNameThatLooksLikeAFormulaIsDefusedInTheCsv() throws Exception {
        // Arrange
        when(listSubscriptionsByPaymentStatusUseCase.execute(any())).thenReturn(List.of(
                new SubscriptionPaymentEntry(subscription, "=HYPERLINK(\"http://evil.example\",\"x\")"),
                new SubscriptionPaymentEntry(subscription, "+1+1"), new SubscriptionPaymentEntry(subscription, "-2"),
                new SubscriptionPaymentEntry(subscription, "@SUM(A1)"), new SubscriptionPaymentEntry(subscription, "Silva, Ana")));

        // Act
        String csv = authenticated(HttpMethod.GET, "/api/v1/subscriptions/export?paymentStatus=PENDING").andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Assert
        assertThat(csv).contains(",\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"x\"\")\",")
                .contains(",'+1+1,").contains(",'-2,").contains(",'@SUM(A1),").contains(",\"Silva, Ana\",");
        assertThat(csv).doesNotContain(",=HYPERLINK").doesNotContain(",+1+1,").doesNotContain(",@SUM");
    }

    @Test
    void markingOverdueAnswersWithTheSubscription() throws Exception {
        // Arrange
        when(markSubscriptionOverdueUseCase.execute(any())).thenReturn(subscription.markOverdue());

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/subscriptions/" + subscription.id() + "/overdue-marking");

        // Assert
        result.andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("OVERDUE"));
        ArgumentCaptor<MarkSubscriptionOverdueCommand> command = ArgumentCaptor.forClass(MarkSubscriptionOverdueCommand.class);
        verify(markSubscriptionOverdueUseCase).execute(command.capture());
        assertThat(command.getValue().subscriptionId()).isEqualTo(subscription.id());
    }

    @Test
    void recordingAPaymentMapsCentsAndMethodAndShowsWhatIsStillOwed() throws Exception {
        // Arrange
        Payment payment = WebFixtures.payment(subscription, member.id());
        when(recordPaymentUseCase.execute(any())).thenReturn(new PaymentRecorded(payment, subscription, Money.ofCents(3000)));

        // Act
        var result = authenticated(HttpMethod.POST, "/api/v1/subscriptions/" + subscription.id() + "/payments",
                "{\"amountCents\":1500,\"paidOn\":\"2026-10-12\",\"method\":\"MB_WAY\"}");

        // Assert
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.id").value(payment.id().value().toString()))
                .andExpect(jsonPath("$.payment.amountCents").value(1500))
                .andExpect(jsonPath("$.payment.method").value("CASH"))
                .andExpect(jsonPath("$.payment.reversalOf").doesNotExist())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.outstandingCents").value(3000));
        ArgumentCaptor<RecordPaymentCommand> command = ArgumentCaptor.forClass(RecordPaymentCommand.class);
        verify(recordPaymentUseCase).execute(command.capture());
        assertThat(command.getValue().amount().cents()).isEqualTo(1500);
        assertThat(command.getValue().method()).isEqualTo(PaymentMethod.MB_WAY);
        assertThat(command.getValue().paidOn()).isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(command.getValue().subscriptionId()).isEqualTo(subscription.id());
        assertThat(command.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void aPaymentOfZeroOrNegativeMoneyOrAnUnknownMethodIs400() throws Exception {
        // Arrange
        String path = "/api/v1/subscriptions/" + subscription.id() + "/payments";

        // Act
        var zero = authenticated(HttpMethod.POST, path, "{\"amountCents\":0,\"paidOn\":\"2026-10-12\",\"method\":\"CASH\"}");
        var negative = authenticated(HttpMethod.POST, path, "{\"amountCents\":-5,\"paidOn\":\"2026-10-12\",\"method\":\"CASH\"}");
        var method = authenticated(HttpMethod.POST, path, "{\"amountCents\":5,\"paidOn\":\"2026-10-12\",\"method\":\"BITCOIN\"}");
        var missing = authenticated(HttpMethod.POST, path, "{}");
        var ancient = authenticated(HttpMethod.POST, path, "{\"amountCents\":5,\"paidOn\":\"1999-12-31\",\"method\":\"CASH\"}");

        // Assert
        ancient.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("paidOn"));
        zero.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields[0]").value("amountCents"));
        negative.andExpect(status().isBadRequest());
        method.andExpect(status().isBadRequest());
        missing.andExpect(status().isBadRequest());
        verifyNoInteractions(recordPaymentUseCase);
    }
}
