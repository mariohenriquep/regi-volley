package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MarkSubscriptionOverdueCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

/**
 * RN-18. An administrator marks a PENDING subscription as OVERDUE: from then on its member cannot book (decided
 * 7/10/2026) and the subscription shows in the overdue list (US-22). Only the subscription's transition map decides
 * what may be marked (PAID and already OVERDUE ones are refused). Paying it in full makes it PAID again.
 */
@Service
public class MarkSubscriptionOverdueService implements MarkSubscriptionOverdueUseCase {

    private final MemberRepository members;
    private final SubscriptionRepository subscriptions;
    private final UnitOfWork unitOfWork;

    public MarkSubscriptionOverdueService(MemberRepository members, SubscriptionRepository subscriptions,
                                          TransactionRunner transactions) {
        this.members = members;
        this.subscriptions = subscriptions;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public Subscription execute(MarkSubscriptionOverdueCommand command) {
        return unitOfWork.retrying(() -> {
            var associationId = command.actor().associationId();
            Member admin = Lookups.member(members, associationId, command.actor().memberId());
            Permissions.requireAdmin(admin, "mark subscriptions as overdue");
            Subscription subscription = Lookups.subscription(subscriptions, associationId, command.subscriptionId());
            return subscriptions.save(subscription.markOverdue());
        });
    }
}
