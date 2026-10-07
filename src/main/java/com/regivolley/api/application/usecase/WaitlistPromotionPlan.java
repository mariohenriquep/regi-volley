package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.service.BookingEligibility;
import com.regivolley.api.domain.service.BookingTarget;
import com.regivolley.api.domain.service.MemberBookingProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** The {@link PromotionPlan} of a session with a waitlist: the facts of each waiting member, loaded by {@link SeatPromoter}. */
final class WaitlistPromotionPlan implements PromotionPlan {

    private final BookingTarget target;
    private final Map<MemberId, MemberBookingProfile> profiles;

    WaitlistPromotionPlan(BookingTarget target, Map<MemberId, MemberBookingProfile> profiles) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.profiles = Map.copyOf(profiles);
    }

    @Override
    public Predicate<MemberId> eligibility() {
        return BookingEligibility.promotionFilter(profiles.values(), target);
    }

    @Override
    public List<Subscription> charge(List<Booking> promoted) {
        return promoted.stream().flatMap(booking -> chargeOf(booking).stream()).toList();
    }

    /** The charged subscription; empty when the booking already held its place (nothing to save). */
    private Optional<Subscription> chargeOf(Booking booking) {
        MemberBookingProfile profile = profiles.get(booking.memberId());
        Subscription chosen = BookingEligibility.evaluate(profile, target).requireEligible();
        Subscription charged = chosen.consume(booking.id(), target.sessionStart(), profile.subscriptions());
        return charged == chosen ? Optional.empty() : Optional.of(charged);
    }
}
