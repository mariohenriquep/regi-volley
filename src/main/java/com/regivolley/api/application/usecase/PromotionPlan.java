package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.util.List;
import java.util.function.Predicate;

/**
 * The two halves of promoting people off a waitlist. {@link #eligibility()} only <em>selects</em> who
 * the session may promote; it reserves nothing. {@link #charge} then works out the charge of everyone
 * who was promoted, by running eligibility again and consuming on the subscription it picks, counting the
 * member's other subscriptions for weekly allowances (RN-15, RN-16): the contract of
 * {@code BookingEligibility.promotionFilter}. Pure: it writes nothing, the caller saves what it returns
 * in the same transaction as the session.
 */
interface PromotionPlan {

    Predicate<MemberId> eligibility();

    /** The subscriptions, already charged, that the caller must save: one per promoted booking. */
    List<Subscription> charge(List<Booking> promoted);

    /** The plan for a session without a waitlist: nobody to promote, nothing to charge, nothing loaded. */
    static PromotionPlan nobodyWaiting() {
        return new PromotionPlan() {
            @Override
            public Predicate<MemberId> eligibility() {
                return member -> false;
            }

            @Override
            public List<Subscription> charge(List<Booking> promoted) {
                return List.of();
            }
        };
    }
}
