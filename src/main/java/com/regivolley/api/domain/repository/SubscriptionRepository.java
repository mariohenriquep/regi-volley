package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.SubscriptionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.PaymentStatus;
import com.regivolley.api.domain.model.valueobject.SubscriptionId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Port for {@link Subscription} aggregates, credit usages included. Every read is scoped to one association (architecture.md section 8). */
public interface SubscriptionRepository {

    Optional<Subscription> findById(AssociationId associationId, SubscriptionId id);

    /** All subscriptions of the member, oldest period first (overlap check, balance, eligibility). */
    List<Subscription> findByMember(AssociationId associationId, MemberId memberId);

    /**
     * The association's subscriptions in that payment status whose end date lies in {@code [endingFrom, endingTo]} (both included), those
     * ending first first (US-22: who is overdue). The window keeps the list, and the CSV made from it, bounded.
     */
    List<Subscription> findByPaymentStatus(AssociationId associationId, PaymentStatus status, LocalDate endingFrom, LocalDate endingTo);

    /**
     * Inserts a new subscription or updates an existing one with its usages, and returns it as stored,
     * with its new version (each save moves it forward by one); keep working with the returned instance.
     *
     * @throws SubscriptionModifiedConcurrentlyException if the stored subscription is no longer at the
     *         version of {@code subscription} (someone saved in between), if it has a version but no stored
     *         row in its association, or if its row could not be locked in time; nothing is written. Retry
     *         in a new transaction.
     */
    Subscription save(Subscription subscription);
}
