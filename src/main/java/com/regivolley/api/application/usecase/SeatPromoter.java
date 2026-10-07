package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.service.BookingTarget;
import com.regivolley.api.domain.service.MemberBookingProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the facts a waitlist promotion needs (RN-09): for each waitlisted member of a session, their
 * status, level and subscriptions. The result is a {@link PromotionPlan}: the predicate the aggregate
 * asks, and afterwards the charging of whoever it promoted.
 */
final class SeatPromoter {

    private final MemberRepository members;
    private final SubscriptionRepository subscriptions;
    private final TrainingGroupRepository groups;

    SeatPromoter(MemberRepository members, SubscriptionRepository subscriptions, TrainingGroupRepository groups) {
        this.members = members;
        this.subscriptions = subscriptions;
        this.groups = groups;
    }

    PromotionPlan planFor(Association association, Session session) {
        List<MemberId> waiting = session.waitlist().stream().map(Booking::memberId).toList();
        if (waiting.isEmpty()) {
            return PromotionPlan.nobodyWaiting();
        }
        TrainingGroup group = Lookups.group(groups, association.id(), session.trainingGroupId());
        BookingTarget target = BookingTarget.of(session, association.ranksOf(group.acceptedLevels()));
        Map<MemberId, MemberBookingProfile> profiles = new LinkedHashMap<>();
        for (MemberId memberId : waiting) {
            members.findById(association.id(), memberId).ifPresent(member -> profiles.put(memberId,
                    member.bookingProfile(association, subscriptions.findByMember(association.id(), memberId))));
        }
        return new WaitlistPromotionPlan(target, profiles);
    }
}
