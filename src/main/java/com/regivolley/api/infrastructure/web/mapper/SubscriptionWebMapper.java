package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.AssignPlanCommand;
import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.command.MarkSubscriptionOverdueCommand;
import com.regivolley.api.application.command.MyPlanQuery;
import com.regivolley.api.application.command.RecordPaymentCommand;
import com.regivolley.api.application.command.ReversePaymentCommand;
import com.regivolley.api.application.result.MyPlan;
import com.regivolley.api.application.result.MySubscription;
import com.regivolley.api.application.result.PaymentRecorded;
import com.regivolley.api.application.result.PaymentReversed;
import com.regivolley.api.application.result.SubscriptionPaymentEntry;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentId;
import com.regivolley.api.domain.model.valueobject.PaymentMethod;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.infrastructure.web.dto.AssignPlanRequest;
import com.regivolley.api.infrastructure.web.dto.MyPlanResponse;
import com.regivolley.api.infrastructure.web.dto.PaymentMethodName;
import com.regivolley.api.infrastructure.web.dto.PaymentRecordedResponse;
import com.regivolley.api.infrastructure.web.dto.PaymentResponse;
import com.regivolley.api.infrastructure.web.dto.PaymentStatusName;
import com.regivolley.api.infrastructure.web.dto.PaymentReversedResponse;
import com.regivolley.api.infrastructure.web.dto.RecordPaymentRequest;
import com.regivolley.api.infrastructure.web.dto.SubscriptionPaymentEntryResponse;
import com.regivolley.api.infrastructure.web.dto.SubscriptionResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Subscriptions and payments (US-20 to US-23). Money is in cents on the wire. */
public final class SubscriptionWebMapper {

    private SubscriptionWebMapper() {
    }

    public static AssignPlanCommand assignCommand(Actor actor, UUID memberId, AssignPlanRequest body) {
        return new AssignPlanCommand(actor, MemberId.of(memberId), PlanId.of(body.planId()), body.startDate());
    }

    public static RecordPaymentCommand recordPaymentCommand(Actor actor, UUID subscriptionId, RecordPaymentRequest body) {
        return new RecordPaymentCommand(actor, SubscriptionId.of(subscriptionId), Money.ofCents(body.amountCents()), body.paidOn(),
                toDomain(body.method()));
    }

    public static ReversePaymentCommand reversePaymentCommand(Actor actor, UUID paymentId) {
        return new ReversePaymentCommand(actor, PaymentId.of(paymentId));
    }

    public static MarkSubscriptionOverdueCommand overdueCommand(Actor actor, UUID subscriptionId) {
        return new MarkSubscriptionOverdueCommand(actor, SubscriptionId.of(subscriptionId));
    }

    public static ListSubscriptionsByPaymentStatusQuery listQuery(Actor actor, PaymentStatusName status, LocalDate endingFrom,
                                                                  LocalDate endingTo) {
        return new ListSubscriptionsByPaymentStatusQuery(actor, toDomain(status), endingFrom, endingTo);
    }

    public static MyPlanQuery myPlanQuery(Actor actor) {
        return new MyPlanQuery(actor);
    }

    public static SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(subscription.id().value(), subscription.memberId().value(), subscription.planId().value(),
                subscription.type().name(), subscription.startDate(), subscription.endDate(), subscription.paymentStatus().name(),
                subscription.price().cents(), subscription.creditsUsed());
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.id().value(), payment.subscriptionId().value(), payment.amount().cents(), payment.paidOn(),
                payment.method().name(), payment.recordedBy().value(), payment.recordedAt(),
                payment.reversalOf().map(PaymentId::value).orElse(null));
    }

    public static PaymentRecordedResponse toResponse(PaymentRecorded recorded) {
        return new PaymentRecordedResponse(toResponse(recorded.payment()), recorded.subscription().paymentStatus().name(),
                recorded.outstanding().cents());
    }

    public static PaymentReversedResponse toResponse(PaymentReversed reversed) {
        return new PaymentReversedResponse(toResponse(reversed.reversal()), reversed.subscription().paymentStatus().name(),
                reversed.outstanding().cents());
    }

    public static List<SubscriptionPaymentEntryResponse> toEntryResponses(List<SubscriptionPaymentEntry> entries) {
        return entries.stream().map(SubscriptionWebMapper::toResponse).toList();
    }

    static SubscriptionPaymentEntryResponse toResponse(SubscriptionPaymentEntry entry) {
        Subscription subscription = entry.subscription();
        return new SubscriptionPaymentEntryResponse(subscription.id().value(), subscription.memberId().value(), entry.memberName(),
                subscription.planId().value(), subscription.type().name(), subscription.startDate(), subscription.endDate(),
                subscription.paymentStatus().name(), subscription.price().cents());
    }

    public static MyPlanResponse toResponse(MyPlan plan) {
        return new MyPlanResponse(plan.asOf(), plan.subscriptions().stream().map(SubscriptionWebMapper::toResponse).toList());
    }

    private static MyPlanResponse.SubscriptionView toResponse(MySubscription subscription) {
        return new MyPlanResponse.SubscriptionView(subscription.subscriptionId().value(), subscription.planName(),
                subscription.type().name(), subscription.startDate(), subscription.endDate(), subscription.paymentStatus().name(),
                subscription.remaining().isPresent() ? subscription.remaining().getAsInt() : null);
    }

    /** Every wire spelling has its domain status; a new value on either side stops the build here. */
    static PaymentStatus toDomain(PaymentStatusName name) {
        return switch (name) {
            case PENDING -> PaymentStatus.PENDING;
            case PAID -> PaymentStatus.PAID;
            case OVERDUE -> PaymentStatus.OVERDUE;
        };
    }

    static PaymentMethod toDomain(PaymentMethodName name) {
        return switch (name) {
            case CASH -> PaymentMethod.CASH;
            case TRANSFER -> PaymentMethod.TRANSFER;
            case MB_WAY -> PaymentMethod.MB_WAY;
        };
    }
}
