package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.exception.SubscriptionOverlapException;
import com.regivolley.api.domain.model.entity.Plan;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.CreditUsage;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.Money;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.PlanId;
import com.regivolley.api.domain.model.valueobject.PlanTerms;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Creates, renews and reconstitutes {@link Subscription}s: the only place a subscription is born.
 * {@link Subscription}'s constructor checks every invariant, so no path can produce an invalid subscription. Stateless,
 * so its methods are static.
 *
 * <p>Assigning a plan spans two aggregates and the member's other subscriptions, which none of them can see on its own,
 * so the rules live here: the period follows from the plan, the plan's terms and <b>price</b> are snapshotted (editing the
 * plan later never changes what the subscription owes), and the period may not overlap an active subscription the member
 * already has (RN-16).
 */
public final class SubscriptionFactory {

    private SubscriptionFactory() {
    }

    /**
     * Assigns {@code plan} to a member from {@code startDate}; the end follows from the plan (US-20). Starts PENDING until
     * a payment is registered, at version 0. Rejected if the period overlaps any <em>active</em> subscription the member
     * already has (RN-16): a PACK or SINGLE_SESSION subscription with no credits left is not active (see
     * {@link Subscription#isExhausted()}), so a new pack may start while the old one's period still runs. Other members'
     * subscriptions in {@code existing} are ignored, but every one of them must belong to the plan's association
     * (architecture.md section 8).
     *
     * @throws SubscriptionOverlapException if the period overlaps an active subscription of the member
     * @throws IllegalArgumentException     if {@code existing} holds a subscription of another association
     */
    public static Subscription create(Plan plan, MemberId memberId, LocalDate startDate,
                                      Collection<Subscription> existing) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(startDate, "startDate must not be null");
        Objects.requireNonNull(existing, "existing must not be null");
        if (existing.stream().anyMatch(other -> !other.associationId().equals(plan.associationId()))) {
            throw new IllegalArgumentException("Existing subscriptions must belong to the plan's association");
        }
        LocalDate endDate = plan.endDateFor(startDate);
        existing.stream()
                .filter(other -> other.memberId().equals(memberId))
                .filter(other -> !other.isExhausted())
                .filter(other -> other.overlaps(startDate, endDate))
                .findFirst()
                .ifPresent(other -> {
                    throw new SubscriptionOverlapException(other.startDate(), other.endDate());
                });
        return new Subscription(SubscriptionId.generate(), plan.associationId(), memberId, plan.id(), plan.terms(),
                plan.price(), startDate, endDate, PaymentStatus.PENDING, List.of(), 0L);
    }

    /**
     * Renews {@code previous} (RN-16). A monthly subscription is renewed the day after it ends; a pack or single session
     * whose credits are used up is renewed at once, on {@code today} (see {@link Subscription#renewalStartDate(LocalDate)}).
     */
    public static Subscription createRenewal(Plan plan, Subscription previous, Collection<Subscription> existing,
                                             LocalDate today) {
        Objects.requireNonNull(previous, "previous must not be null");
        return create(plan, previous.memberId(), previous.renewalStartDate(today), existing);
    }

    /**
     * Rebuilds a subscription from persisted data.
     *
     * @param version the optimistic-lock version it was loaded with; it is what stops two concurrent bookings of one
     *                member from spending the same last credit (architecture.md section 10)
     */
    public static Subscription reconstitute(SubscriptionId id, AssociationId associationId, MemberId memberId,
                                            PlanId planId, PlanTerms terms, Money price, LocalDate startDate,
                                            LocalDate endDate, PaymentStatus paymentStatus, List<CreditUsage> usages,
                                            long version) {
        return new Subscription(id, associationId, memberId, planId, terms, price, startDate, endDate, paymentStatus,
                usages, version);
    }
}
