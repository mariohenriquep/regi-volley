package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.ListSubscriptionsByPaymentStatusQuery;
import com.regivolley.api.application.result.SubscriptionPaymentEntry;
import com.regivolley.api.domain.exception.InvalidFieldException;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.ScheduleZone;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
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

    private static final int MAX_WINDOW_YEARS = 2;
    private static final Logger LOG = LoggerFactory.getLogger(ListSubscriptionsByPaymentStatusService.class);

    private final MemberRepository members;
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    public ListSubscriptionsByPaymentStatusService(MemberRepository members, SubscriptionRepository subscriptions, Clock clock) {
        this.members = members;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Override
    public List<SubscriptionPaymentEntry> execute(ListSubscriptionsByPaymentStatusQuery query) {
        var associationId = query.actor().associationId();
        Member admin = Lookups.member(members, associationId, query.actor().memberId());
        Permissions.requireAdmin(admin, "see payment statuses");
        LocalDate[] window = window(query);
        List<Subscription> found = subscriptions.findByPaymentStatus(associationId, query.status(), window[0], window[1]);
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

    /** {@code [from, to]}: what the query says, completed and checked (at most two years, not backwards). */
    private LocalDate[] window(ListSubscriptionsByPaymentStatusQuery query) {
        LocalDate from = query.endingFrom();
        LocalDate to = query.endingTo();
        if (from == null && to == null) {
            LocalDate today = clock.instant().atZone(ScheduleZone.LISBON.zoneId()).toLocalDate();
            from = today.minusYears(1);
            to = today.plusYears(1);
        } else if (to == null) {
            to = from.plusYears(MAX_WINDOW_YEARS);
        } else if (from == null) {
            from = to.minusYears(MAX_WINDOW_YEARS);
        }
        if (from.isAfter(to)) {
            throw new InvalidFieldException("from", "The window must not end before it starts");
        }
        if (to.isAfter(from.plusYears(MAX_WINDOW_YEARS))) {
            throw new InvalidFieldException("to", "The window may span at most " + MAX_WINDOW_YEARS + " years");
        }
        return new LocalDate[]{from, to};
    }
}
