package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.result.SubscriptionPaymentEntry;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * US-22, RN-18. An administrator lists the association's subscriptions in a payment status (typically OVERDUE),
 * ending soonest first, with the member's name (members loaded in one batch; one that cannot be found is
 * logged by id and left out rather than failing the list). CSV export is a web concern.
 */
@Service
public class ListSubscriptionsByPaymentStatusService implements ListSubscriptionsByPaymentStatusUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(ListSubscriptionsByPaymentStatusService.class);

    private final MemberRepository members;
    private final SubscriptionRepository subscriptions;

    public ListSubscriptionsByPaymentStatusService(MemberRepository members, SubscriptionRepository subscriptions) {
        this.members = members;
        this.subscriptions = subscriptions;
    }

    @Override
    public List<SubscriptionPaymentEntry> execute(ListSubscriptionsByPaymentStatusQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "see payment statuses");
        List<Subscription> found = subscriptions.findByPaymentStatus(associationId, query.status());
        if (found.isEmpty()) {
            return List.of();
        }
        Map<MemberId, Member> memberById = members.findByIds(associationId,
                found.stream().map(Subscription::memberId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Member::id, Function.identity()));
        List<SubscriptionPaymentEntry> entries = new ArrayList<>();
        for (Subscription subscription : found) {
            Member member = memberById.get(subscription.memberId());
            if (member == null) {
                LOG.warn("Subscription {} belongs to member {}, who could not be loaded: left out of the list",
                        subscription.id(), subscription.memberId());
                continue;
            }
            entries.add(new SubscriptionPaymentEntry(subscription, member.name()));
        }
        return entries;
    }
}
