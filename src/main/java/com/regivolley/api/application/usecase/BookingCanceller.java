package com.regivolley.api.application.usecase;

import com.regivolley.api.application.result.CancelledBooking;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.result.BookingCancellation;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Cancelling one booking and everything that follows from it (US-15, RN-09, RN-10, RN-15): the
 * aggregate cancels and promotes the waitlist, the session is saved, the credit goes back when the
 * cancellation earns it, and each promoted member is charged. Used by the member's own cancellation
 * ({@link #cancelByMember}) and by deactivating a member ({@link #cancelByAssociation}). Must run inside
 * the caller's transaction; the promotion notices are queued, to be sent after the commit.
 */
final class BookingCanceller {

    private final SessionRepository sessions;
    private final SubscriptionRepository subscriptions;
    private final SeatPromoter promoter;
    private final CreditRefunder refunder;
    private final Clock clock;

    BookingCanceller(SessionRepository sessions, SubscriptionRepository subscriptions, SeatPromoter promoter,
                     CreditRefunder refunder, Clock clock) {
        this.sessions = sessions;
        this.subscriptions = subscriptions;
        this.promoter = promoter;
        this.refunder = refunder;
        this.clock = clock;
    }

    /** Free or late according to the association's policy (RN-10). */
    CancelledBooking cancelByMember(Association association, Session session, BookingId bookingId,
                                    List<Consumer<Notifier>> notifications) {
        return cancel(association, session, notifications, eligibility ->
                session.cancelBooking(bookingId, association.bookingPolicy(), clock, eligibility));
    }

    /** Never late: the association cancels, not the member (US-08). */
    CancelledBooking cancelByAssociation(Association association, Session session, BookingId bookingId,
                                         List<Consumer<Notifier>> notifications) {
        return cancel(association, session, notifications, eligibility ->
                session.cancelBookingByAssociation(bookingId, clock, eligibility));
    }

    private CancelledBooking cancel(Association association, Session session, List<Consumer<Notifier>> notifications,
                                    Function<Predicate<MemberId>, BookingCancellation> domainCancellation) {
        PromotionPlan plan = promoter.planFor(association, session);
        BookingCancellation cancellation = domainCancellation.apply(plan.eligibility());
        Session stored = sessions.save(cancellation.session());
        Optional<Subscription> refunded = refunder.refund(association.id(), cancellation.cancelled());
        refunded.ifPresent(subscriptions::save);
        plan.charge(cancellation.promoted()).forEach(subscriptions::save);
        cancellation.promoted().forEach(promoted -> notifications.add(
                notifier -> notifier.bookingPromoted(association.id(), stored.id(), promoted.memberId())));
        return new CancelledBooking(stored, cancellation.cancelled(), cancellation.isLate(), refunded.isPresent(),
                cancellation.promoted());
    }
}
