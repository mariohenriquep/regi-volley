package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.GetMemberDetailQuery;
import com.regivolley.api.application.identity.Membership;
import com.regivolley.api.application.port.MembershipStore;
import com.regivolley.api.application.result.LevelChangeEntry;
import com.regivolley.api.application.result.MemberDetail;
import com.regivolley.api.application.result.MemberSubscriptionEntry;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Payment;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelChange;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.PaymentRepository;
import com.regivolley.api.domain.repository.PlanRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.service.PaymentLedger;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * US-04, US-07, US-20, US-21. An administrator opens one member of their association: the contact data they hold, the level and
 * how it changed over time (who, when, from and to, every name resolved), whether the account is activated and the member's
 * subscriptions, newest first, with the plan's name, the credits and what has been paid and is still due. The amounts are the
 * {@link PaymentLedger}'s, from the subscription's own price. Level names come from the association, the people who changed the
 * level from one batch, the plans from one batch and all the payments from one batch (no query per row). An anonymised member is
 * shown as the placeholders they became. At most {@link #MAX_SUBSCRIPTIONS} subscriptions are shown; the cut is flagged. The target is
 * looked up in the actor's association only, so another association's member is not found. Read only, so no transaction of its own.
 */
@Service
public class GetMemberDetailService implements GetMemberDetailUseCase {

    /** How many of a member's most recent subscriptions the detail shows. */
    static final int MAX_SUBSCRIPTIONS = 100;

    private final MemberRepository members;
    private final AssociationRepository associations;
    private final MembershipStore memberships;
    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PaymentRepository payments;

    public GetMemberDetailService(MemberRepository members, AssociationRepository associations, MembershipStore memberships,
                                  SubscriptionRepository subscriptions, PlanRepository plans, PaymentRepository payments) {
        this.members = members;
        this.associations = associations;
        this.memberships = memberships;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.payments = payments;
    }

    @Override
    public MemberDetail execute(GetMemberDetailQuery query) {
        AssociationId associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "see a member");
        Member member = Lookups.member(members, associationId, query.memberId());
        Association association = Lookups.association(associations, associationId);

        Map<LevelId, String> levelNames = association.levels().stream().collect(Collectors.toMap(Level::id, Level::name));
        boolean activated = memberships.findByMember(associationId, member.id()).map(Membership::isConfirmed).orElse(false);
        List<Subscription> all = subscriptions.findByMember(associationId, member.id()).stream()
                .sorted(Comparator.comparing(Subscription::startDate).reversed().thenComparing(subscription -> subscription.id().value()))
                .toList();
        boolean truncated = all.size() > MAX_SUBSCRIPTIONS;
        List<Subscription> shown = truncated ? all.subList(0, MAX_SUBSCRIPTIONS) : all;
        return new MemberDetail(member, Optional.ofNullable(levelNames.get(member.levelId())), activated,
                levelChanges(associationId, member, levelNames), subscriptionEntries(associationId, shown), truncated);
    }

    private List<LevelChangeEntry> levelChanges(AssociationId associationId, Member member, Map<LevelId, String> levelNames) {
        List<LevelChange> changes = member.levelChanges();
        if (changes.isEmpty()) {
            return List.of();
        }
        Set<MemberId> changers = changes.stream().map(LevelChange::changedBy).collect(Collectors.toSet());
        Map<MemberId, String> changerNames = members.findByIds(associationId, changers).stream()
                .collect(Collectors.toMap(Member::id, Member::name));
        return changes.stream()
                .map(change -> new LevelChangeEntry(change, Optional.ofNullable(levelNames.get(change.from())),
                        Optional.ofNullable(levelNames.get(change.to())), Optional.ofNullable(changerNames.get(change.changedBy()))))
                .toList();
    }

    private List<MemberSubscriptionEntry> subscriptionEntries(AssociationId associationId, List<Subscription> shown) {
        if (shown.isEmpty()) {
            return List.of();
        }
        Map<PlanId, String> planNames = plans.findByIds(associationId, shown.stream().map(Subscription::planId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Plan::id, Plan::name));
        Map<SubscriptionId, List<Payment>> paymentsOf = payments
                .findBySubscriptions(associationId, shown.stream().map(Subscription::id).toList()).stream()
                .collect(Collectors.groupingBy(Payment::subscriptionId));
        return shown.stream().map(subscription -> {
            PaymentLedger ledger = PaymentLedger.of(subscription, paymentsOf.getOrDefault(subscription.id(), List.of()));
            return new MemberSubscriptionEntry(subscription, Optional.ofNullable(planNames.get(subscription.planId())),
                    ledger.paid(), ledger.outstanding());
        }).toList();
    }
}
