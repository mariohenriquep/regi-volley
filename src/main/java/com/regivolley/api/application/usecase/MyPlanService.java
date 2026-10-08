package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.MyPlanQuery;
import com.regivolley.api.application.result.MyPlan;
import com.regivolley.api.application.result.MySubscription;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-23. The actor's own subscriptions that have not ended yet (current and upcoming, earliest first) as of today
 * in Lisbon: the plan, the validity, what is left (credits, or this week's sessions for a weekly plan; nothing
 * for an unlimited plan) and the payment status. Only the actor's own data is ever read.
 */
@Service
public class MyPlanService implements MyPlanUseCase {

    /** Shown when a subscription's plan cannot be loaded: the member still sees their balance. */
    static final String UNKNOWN_PLAN_NAME = "(plan unavailable)";

    private static final Logger LOG = LoggerFactory.getLogger(MyPlanService.class);

    private final MemberRepository members;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public MyPlanService(MemberRepository members, PlanRepository plans, SubscriptionRepository subscriptions,
                         Clock clock) {
        this.members = members;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Override
    public MyPlan execute(MyPlanQuery query) {
        var associationId = query.actor().associationId();
        Member member = Lookups.member(members, associationId, query.actor().memberId());
        LocalDate today = clock.instant().atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();

        List<Subscription> all = subscriptions.findByMember(associationId, member.id());
        List<Subscription> current = all.stream()
                .filter(subscription -> !subscription.endDate().isBefore(today))
                .sorted(Comparator.comparing(Subscription::startDate))
                .toList();
        Map<PlanId, Plan> planById = plansOf(associationId, current);
        return new MyPlan(today, current.stream()
                .map(subscription -> summary(subscription, planById.get(subscription.planId()), today, all))
                .toList());
    }

    /** One query for all the plans; none at all when there is nothing to show. */
    private Map<PlanId, Plan> plansOf(AssociationId associationId, List<Subscription> shown) {
        if (shown.isEmpty()) {
            return Map.of();
        }
        Set<PlanId> ids = shown.stream().map(Subscription::planId).collect(Collectors.toSet());
        return plans.findByIds(associationId, ids).stream().collect(Collectors.toMap(Plan::id, Function.identity()));
    }

    private MySubscription summary(Subscription subscription, Plan plan, LocalDate today, List<Subscription> all) {
        String planName = plan == null ? UNKNOWN_PLAN_NAME : plan.name();
        if (plan == null) {
            LOG.warn("Subscription {} points at plan {}, which could not be loaded", subscription.id(), subscription.planId());
        }
        return new MySubscription(subscription.id(), planName, subscription.type(), subscription.startDate(),
                subscription.endDate(), subscription.paymentStatus(), subscription.balanceOn(today, all));
    }
}
