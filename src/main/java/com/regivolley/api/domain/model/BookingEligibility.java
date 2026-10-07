package com.regivolley.api.domain.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure domain policy answering "may this member book this session?" (RN-06, RN-14, RN-18,
 * RN-21). No ports, no clock: the use case loads the facts and asks.
 *
 * <p>Checked in order, the first failure is the reason: member active, level accepted by the
 * group (own level and lower, RN-21), then a subscription that is valid on the session date,
 * whose plan allows the group's levels, with balance, and not overdue. When the member has
 * several subscriptions the first usable one (earliest start) is charged; if none is usable the
 * reason reported is the one closest to bookable (see {@link BookingRejectionReason}).
 *
 * <p>An "N per week" allowance is per member, not per subscription (RN-16): the sessions already
 * charged in the session's week to any of the member's other weekly subscriptions (e.g. the
 * one this renews) count against it, since the profile holds all the member's subscriptions.
 *
 * <p>Profiles and subscriptions of another association than the target's are rejected with an
 * {@link IllegalArgumentException} (architecture.md section 8): that is a programming error,
 * not a booking rejection.
 */
public final class BookingEligibility {

    private BookingEligibility() {
    }

    public static EligibilityDecision evaluate(MemberBookingProfile profile, BookingTarget target) {
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(target, "target must not be null");
        requireSameAssociation(profile, target);
        if (profile.status() != MemberStatus.ACTIVE) {
            return EligibilityDecision.rejected(BookingRejectionReason.MEMBER_INACTIVE);
        }
        if (!profile.level().canBookGroupAccepting(target.acceptedLevels())) {
            return EligibilityDecision.rejected(BookingRejectionReason.LEVEL_NOT_ALLOWED);
        }
        return chooseSubscription(profile.subscriptions(), target);
    }

    private static void requireSameAssociation(MemberBookingProfile profile, BookingTarget target) {
        if (!profile.associationId().equals(target.associationId())) {
            throw new IllegalArgumentException("The member and the session belong to different associations");
        }
    }

    private static EligibilityDecision chooseSubscription(List<Subscription> subscriptions, BookingTarget target) {
        Set<LevelId> groupLevels = target.acceptedLevelIds();
        List<Subscription> byStart = subscriptions.stream()
                .sorted(Comparator.comparing(Subscription::startDate))
                .toList();
        BookingRejectionReason closest = BookingRejectionReason.NO_VALID_SUBSCRIPTION;
        for (Subscription subscription : byStart) {
            Optional<BookingRejectionReason> rejection =
                    subscription.rejectionFor(target.sessionStart(), groupLevels, subscriptions);
            if (rejection.isEmpty()) {
                return EligibilityDecision.eligible(subscription);
            }
            if (rejection.get().isCloserToBookableThan(closest)) {
                closest = rejection.get();
            }
        }
        return EligibilityDecision.rejected(closest);
    }

    /**
     * RN-09 seam: the {@code Predicate<MemberId>} that {@link Session#promoteWaitlist},
     * {@link Session#cancelBooking} and {@link Session#changeCapacity} expect, built from the
     * profiles of the waitlisted members. A member is promotable when eligible; a member without
     * a profile is not (unknown facts never grant a seat).
     *
     * <p>The predicate only <em>selects</em> who is promoted. Eligibility is not a reservation:
     * for each promoted booking the use case must run {@link #evaluate} again and
     * {@link Subscription#consume} on the subscription it returns (passing the member's
     * subscriptions), persisting the result, because the predicate does not charge anything.
     *
     * @throws IllegalArgumentException for two profiles of the same member or a profile of
     *                                  another association than the target's
     */
    public static Predicate<MemberId> promotionFilter(Collection<MemberBookingProfile> profiles,
                                                      BookingTarget target) {
        Objects.requireNonNull(target, "target must not be null");
        Map<MemberId, MemberBookingProfile> byMember = new HashMap<>();
        for (MemberBookingProfile profile : profiles) {
            requireSameAssociation(profile, target);
            if (byMember.put(profile.memberId(), profile) != null) {
                throw new IllegalArgumentException("Duplicate profile for member " + profile.memberId());
            }
        }
        return memberId -> {
            MemberBookingProfile profile = byMember.get(memberId);
            return profile != null && evaluate(profile, target).isEligible();
        };
    }
}
