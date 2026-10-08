package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CancelSessionCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.CancelledSession;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * US-11, RN-04, RN-15. The coach of the session or an administrator cancels it with a reason. The
 * aggregate cancels every live booking and says which held a credit; this refunds exactly those
 * ({@link Session#bookingsToRefund()}) and notifies everyone the session cancelled, waitlisted
 * members included, after the commit.
 */
@Service
public class CancelSessionService implements CancelSessionUseCase {

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final SubscriptionRepository subscriptions;
    private final CreditRefunder refunder;
    private final Notifier notifier;
    private final UnitOfWork unitOfWork;

    public CancelSessionService(SessionRepository sessions, MemberRepository members,
                                SubscriptionRepository subscriptions, TransactionRunner transactions, Notifier notifier) {
        this.sessions = sessions;
        this.members = members;
        this.subscriptions = subscriptions;
        this.refunder = new CreditRefunder(subscriptions);
        this.notifier = notifier;
        this.unitOfWork = new UnitOfWork(transactions);
    }

    @Override
    public CancelledSession execute(CancelSessionCommand command) {
        return unitOfWork.retryingAndNotify(() -> attempt(command), notifier);
    }

    private Outcome<CancelledSession> attempt(CancelSessionCommand command) {
        var associationId = command.actor().associationId();
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Session session = Lookups.session(sessions, associationId, command.sessionId());
        Permissions.requireStaffOf(actor, session, "cancel this session");

        Session cancelled = session.cancel(command.reason());
        Session stored = sessions.save(cancelled);

        int refunded = 0;
        for (Booking booking : cancelled.bookingsToRefund()) {
            Optional<Subscription> refund = refunder.refund(associationId, booking);
            if (refund.isPresent()) {
                subscriptions.save(refund.get());
                refunded++;
            }
        }
        List<Booking> notified = cancelled.bookingsCancelledBySession();
        List<Consumer<Notifier>> notifications = new ArrayList<>();
        notified.forEach(booking -> notifications.add(n -> n.sessionCancelled(
                associationId, stored.id(), booking.memberId(), command.reason())));
        return Outcome.of(new CancelledSession(stored, notified, refunded), notifications);
    }
}
