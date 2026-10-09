package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.command.MemberHistoryQuery;
import com.regivolley.api.application.command.MyPlanQuery;
import com.regivolley.api.application.result.HistoryEntry;
import com.regivolley.api.application.result.MemberHistory;
import com.regivolley.api.application.result.MyPlan;
import com.regivolley.api.application.result.MySubscription;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanType;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-18 and US-23 over HTTP: a member reads their own plan and history. */
class MemberSelfControllerTest extends AbstractControllerWebTest {

    @Test
    void myPlanShowsTheBalanceAndOmitsItForAnUnlimitedPlan() throws Exception {
        // Arrange
        when(myPlanUseCase.execute(any())).thenReturn(new MyPlan(LocalDate.parse("2026-10-12"), List.of(
                new MySubscription(SubscriptionId.generate(), "Ten sessions", PlanType.PACK, LocalDate.parse("2026-10-01"),
                        LocalDate.parse("2026-12-30"), PaymentStatus.PAID, OptionalInt.of(7)),
                new MySubscription(SubscriptionId.generate(), "Monthly", PlanType.MONTHLY_UNLIMITED, LocalDate.parse("2026-10-01"),
                        LocalDate.parse("2026-10-31"), PaymentStatus.PENDING, OptionalInt.empty()))));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/me/plan");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").value("2026-10-12"))
                .andExpect(jsonPath("$.subscriptions[0].planName").value("Ten sessions"))
                .andExpect(jsonPath("$.subscriptions[0].type").value("PACK"))
                .andExpect(jsonPath("$.subscriptions[0].remaining").value(7))
                .andExpect(jsonPath("$.subscriptions[1].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.subscriptions[1].remaining").doesNotExist());
        ArgumentCaptor<MyPlanQuery> query = ArgumentCaptor.forClass(MyPlanQuery.class);
        verify(myPlanUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
    }

    @Test
    void myHistoryShowsTheEntriesAndTheNoShowCount() throws Exception {
        // Arrange
        Instant start = Instant.parse("2026-10-07T19:00:00Z");
        BookingId bookingId = BookingId.generate();
        when(memberHistoryUseCase.execute(any())).thenReturn(new MemberHistory(List.of(
                new HistoryEntry(SessionId.generate(), TrainingGroupId.generate(), start, start.plusSeconds(5400), bookingId, BookingStatus.ATTENDED)),
                2, 3, true));

        // Act
        var result = authenticated(HttpMethod.GET, "/api/v1/me/history");

        // Assert
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].bookingId").value(bookingId.value().toString()))
                .andExpect(jsonPath("$.entries[0].status").value("ATTENDED"))
                .andExpect(jsonPath("$.noShowsThisMonth").value(2))
                .andExpect(jsonPath("$.noShowLimit").value(3))
                .andExpect(jsonPath("$.nearLimit").value(true));
        ArgumentCaptor<MemberHistoryQuery> query = ArgumentCaptor.forClass(MemberHistoryQuery.class);
        verify(memberHistoryUseCase).execute(query.capture());
        assertThat(query.getValue().actor()).isEqualTo(expectedActor());
    }
}
