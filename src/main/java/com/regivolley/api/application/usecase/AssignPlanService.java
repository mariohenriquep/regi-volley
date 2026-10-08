package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.AssignPlanCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.domain.exception.MemberInactiveException;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.factory.SubscriptionFactory;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.service.PaymentLedger;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * US-20, RN-16. An administrator gives a member a plan: a new PENDING subscription with a snapshot of the plan's
 * terms. With a start date the subscription is created for it; without one it is a renewal of the member's latest
 * subscription (the day after it ends, or at once for an exhausted pack) or, for a member with none, starts today in
 * Lisbon. The subscription factory refuses a period that overlaps an active subscription of the member.
 *
 * <p>Two assignments to one member at once could both pass the overlap check, so the member's row lock is
 * taken first (as when booking, after the administrator has been authorised) and the subscriptions are read after it. A deactivated member is refused. A plan with a price of zero needs no
 * payment: its subscription starts PAID.
 */
@Service
public class AssignPlanService implements AssignPlanUseCase {

    private final MemberRepository members;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AssignPlanService(MemberRepository members, PlanRepository plans, SubscriptionRepository subscriptions,
                             TransactionRunner transactions, Clock clock) {
        this.members = members;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public Subscription execute(AssignPlanCommand command) {
        return unitOfWork.retrying(() -> attempt(command));
    }

    private Subscription attempt(AssignPlanCommand command) {
        var associationId = command.actor().associationId();
        Member admin = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdmin(admin, "assign plans");
        Member target = members.findByIdForUpdate(associationId, command.memberId())
                .orElseThrow(() -> new MemberNotFoundException(command.memberId()));
        if (!target.isActive()) {
            throw new MemberInactiveException(target.id());
        }
        Plan plan = Lookups.plan(plans, associationId, command.planId());

        List<Subscription> existing = subscriptions.findByMember(associationId, target.id());
        Subscription created = command.startDate() != null
                ? SubscriptionFactory.create(plan, target.id(), command.startDate(), existing)
                : startAsSoonAsPossible(plan, target, existing);
        return subscriptions.save(PaymentLedger.of(created, List.of()).settle(created));
    }

    private Subscription startAsSoonAsPossible(Plan plan, Member target, List<Subscription> existing) {
        LocalDate today = clock.instant().atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();
        Optional<Subscription> latest = existing.stream().max(Comparator.comparing(Subscription::endDate));
        return latest.isPresent()
                ? SubscriptionFactory.createRenewal(plan, latest.get(), existing, today)
                : SubscriptionFactory.create(plan, target.id(), today, existing);
    }
}
