package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.BookingNotAllowedException;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of {@link BookingEligibility}: either ELIGIBLE, with the subscription to charge, or
 * rejected with exactly one {@link BookingRejectionReason}.
 */
public final class EligibilityDecision {

    private final Subscription subscription;
    private final BookingRejectionReason rejection;

    private EligibilityDecision(Subscription subscription, BookingRejectionReason rejection) {
        this.subscription = subscription;
        this.rejection = rejection;
    }

    static EligibilityDecision eligible(Subscription subscription) {
        return new EligibilityDecision(Objects.requireNonNull(subscription, "subscription must not be null"), null);
    }

    static EligibilityDecision rejected(BookingRejectionReason reason) {
        return new EligibilityDecision(null, Objects.requireNonNull(reason, "reason must not be null"));
    }

    public boolean isEligible() {
        return subscription != null;
    }

    /** The subscription the booking is charged to; present only when eligible. */
    public Optional<Subscription> subscriptionToCharge() {
        return Optional.ofNullable(subscription);
    }

    /** Present only when rejected. */
    public Optional<BookingRejectionReason> rejection() {
        return Optional.ofNullable(rejection);
    }

    /**
     * The subscription to charge, or a {@link BookingNotAllowedException} carrying the reason.
     * For use cases that turn a rejection into a clear error (US-14).
     */
    public Subscription requireEligible() {
        if (subscription == null) {
            throw new BookingNotAllowedException(rejection);
        }
        return subscription;
    }

    @Override
    public String toString() {
        return isEligible() ? "ELIGIBLE(" + subscription.id() + ")" : "REJECTED(" + rejection + ")";
    }
}
