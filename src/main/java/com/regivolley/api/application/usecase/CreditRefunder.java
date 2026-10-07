package com.regivolley.api.application.usecase;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.repository.SubscriptionRepository;

import java.util.List;
import java.util.Optional;

/**
 * Works out the refund of a cancelled booking's credit (RN-15): which subscription holds the place and
 * what it looks like with the place given back. Reads only; the caller saves the result in its transaction.
 */
final class CreditRefunder {

    private final SubscriptionRepository subscriptions;

    CreditRefunder(SubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * @return the subscription to save with the credit given back; empty for a late cancellation, a
     *         waitlisted booking or one that holds no place
     */
    Optional<Subscription> refund(AssociationId associationId, Booking cancelled) {
        if (!cancelled.creditRefundable()) {
            return Optional.empty();
        }
        List<Subscription> held = subscriptions.findByMember(associationId, cancelled.memberId());
        return Subscription.holdingPlaceFor(cancelled.id(), held).map(holder -> holder.refundFor(cancelled));
    }
}
