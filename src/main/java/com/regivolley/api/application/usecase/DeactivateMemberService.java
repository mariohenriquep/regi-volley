package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.DeactivateMemberCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.MemberDeactivated;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * US-08. An administrator deactivates a member; their history stays, their future bookings go.
 *
 * <p>Step one stores the INACTIVE member (skipped if already inactive, so a run that stopped halfway can
 * be repeated). The last active administrator cannot be deactivated ({@link AdminGuard}). Then each session where they still hold a seat or a waitlist place is handled in its own
 * transaction with its own retries: the session decides whether it still takes cancellations and cancels
 * the booking <em>by the association</em> (never late, credit refundable; the waitlist is promoted, charged
 * and notified like for any cancellation) - this service only loads and calls. A session that fails even
 * after the retries is logged by id and reported in {@link MemberDeactivated#failedSessions()}; it never
 * stops the others and never undoes the deactivation. Rerunning retries exactly the remaining ones.
 */
@Service
public class DeactivateMemberService implements DeactivateMemberUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(DeactivateMemberService.class);

    /** Sessions are generated at most 12 weeks ahead (RN-01); a year ahead covers every future booking. */
    private static final Duration HORIZON = Duration.ofDays(366);

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final BookingCanceller canceller;
    private final Notifier notifier;
    private final AdminGuard adminGuard;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public DeactivateMemberService(SessionRepository sessions, MemberRepository members,
                                   AssociationRepository associations, TrainingGroupRepository groups,
                                   SubscriptionRepository subscriptions, TransactionRunner transactions,
                                   Notifier notifier, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.associations = associations;
        this.canceller = new BookingCanceller(sessions, subscriptions, new SeatPromoter(members, subscriptions, groups),
                new CreditRefunder(subscriptions), clock);
        this.notifier = notifier;
        this.adminGuard = new AdminGuard(associations, members);
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public MemberDeactivated execute(DeactivateMemberCommand command) {
        AssociationId associationId = command.actor().associationId();
        Member deactivated = unitOfWork.retrying(() -> deactivate(command));

        Instant now = clock.instant();
        int cancelled = 0;
        List<SessionId> failed = new ArrayList<>();
        for (Session session : sessions.findWithLiveBookingOverlapping(associationId, deactivated.id(), now,
                now.plus(HORIZON))) {
            try {
                boolean done = unitOfWork.retryingAndNotify(
                        () -> cancelBookingIn(associationId, session.id(), deactivated.id()), notifier);
                if (done) {
                    cancelled++;
                }
            } catch (RuntimeException e) {
                failed.add(session.id());
                LOG.error("Deactivating member {}: the booking in session {} could not be cancelled: {}",
                        deactivated.id(), session.id(), e.getClass().getSimpleName());
            }
        }
        // Audit line (threat model M5), by id only.
        LOG.info("Member deactivated: associationId={} memberId={} deactivatedBy={} bookingsCancelled={} sessionsFailed={}", associationId,
                deactivated.id(), command.actor().memberId(), cancelled, failed.size());
        return new MemberDeactivated(deactivated, cancelled, failed);
    }

    private Member deactivate(DeactivateMemberCommand command) {
        AssociationId associationId = command.actor().associationId();
        adminGuard.serialise(associationId);
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Permissions.requireAdmin(actor, "deactivate members");
        Member target = Lookups.member(members, associationId, command.memberId());
        if (target.status() != MemberStatus.ACTIVE) {
            return target;
        }
        if (Permissions.isAdmin(target)) {
            adminGuard.requireAnotherActiveAdmin(associationId, target.id());
        }
        return members.save(target.deactivate());
    }

    /** Whether a booking was cancelled; false when the session no longer takes cancellations or the member has none. */
    private Outcome<Boolean> cancelBookingIn(AssociationId associationId, SessionId sessionId, MemberId memberId) {
        Session session = Lookups.session(sessions, associationId, sessionId);
        Optional<Booking> held = session.activeBookingOf(memberId);
        if (!session.acceptsCancellations(clock) || held.isEmpty()) {
            return Outcome.silent(false);
        }
        Association association = Lookups.association(associations, associationId);
        List<Consumer<Notifier>> notifications = new ArrayList<>();
        canceller.cancelByAssociation(association, session, held.get().id(), notifications);
        return Outcome.of(true, notifications);
    }
}
