package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionPaymentsQuery;
import com.regivolley.api.application.result.PaymentLine;
import com.regivolley.api.application.result.SubscriptionPayments;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.service.PaymentLedger;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * US-21, RN-17, RN-19. An administrator reads the money side of one subscription of their association: every payment and reversal,
 * oldest first, with the name of the administrator who recorded it (one batch), and the paid and outstanding totals, which are the
 * {@link PaymentLedger}'s (payments minus reversals, against the price the subscription was sold at). A subscription of another
 * association is not found. Read only, so no transaction of its own.
 */
@Service
public class ListSubscriptionPaymentsService implements ListSubscriptionPaymentsUseCase {

    private final MemberRepository members;
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;

    public ListSubscriptionPaymentsService(MemberRepository members, SubscriptionRepository subscriptions, PaymentRepository payments) {
        this.members = members;
        this.subscriptions = subscriptions;
        this.payments = payments;
    }

    @Override
    public SubscriptionPayments execute(ListSubscriptionPaymentsQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "see a subscription's payments");
        Subscription subscription = Lookups.subscription(subscriptions, associationId, query.subscriptionId());

        List<Payment> recorded = payments.findBySubscription(associationId, subscription.id());
        PaymentLedger ledger = PaymentLedger.of(subscription, recorded);
        return new SubscriptionPayments(subscription, lines(associationId, recorded), ledger.paid(), ledger.outstanding());
    }

    private List<PaymentLine> lines(AssociationId associationId, List<Payment> recorded) {
        if (recorded.isEmpty()) {
            return List.of();
        }
        Set<MemberId> recorders = recorded.stream().map(Payment::recordedBy).collect(Collectors.toSet());
        Map<MemberId, String> names = members.findByIds(associationId, recorders).stream()
                .collect(Collectors.toMap(Member::id, Member::name));
        return recorded.stream().map(payment -> new PaymentLine(payment, Optional.ofNullable(names.get(payment.recordedBy())))).toList();
    }
}
