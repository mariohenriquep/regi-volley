package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.CancelBookingCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.CancelledBooking;
import com.regivolley.api.domain.exception.BookingNotFoundException;
import com.regivolley.api.domain.exception.NotAllowedException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.port.Notifier;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * US-15, US-16. The owner of a booking may cancel it, and so may the coach of the session and an
 * administrator (who act for a member who cannot). The aggregate decides free or late (RN-10) and who
 * is promoted (RN-09); see {@link BookingCanceller} for what follows. Promoted members are notified
 * after the commit.
 */
@Service
public class CancelBookingService implements CancelBookingUseCase {

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final Notifier notifier;
    private final UnitOfWork unitOfWork;
    private final BookingCanceller canceller;

    public CancelBookingService(SessionRepository sessions, MemberRepository members, AssociationRepository associations,
                                TrainingGroupRepository groups, SubscriptionRepository subscriptions,
                                TransactionRunner transactions, Notifier notifier, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.associations = associations;
        this.notifier = notifier;
        this.unitOfWork = new UnitOfWork(transactions);
        this.canceller = new BookingCanceller(sessions, subscriptions, new SeatPromoter(members, subscriptions, groups),
                new CreditRefunder(subscriptions), clock);
    }

    @Override
    public CancelledBooking execute(CancelBookingCommand command) {
        return unitOfWork.retryingAndNotify(() -> attempt(command), notifier);
    }

    private Outcome<CancelledBooking> attempt(CancelBookingCommand command) {
        var associationId = command.actor().associationId();
        Member actor = Lookups.member(members, associationId, command.actor().memberId());
        Session session = Lookups.session(sessions, associationId, command.sessionId());
        Booking booking = session.findBooking(command.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(command.bookingId()));
        if (!booking.memberId().equals(actor.id()) && !Permissions.isStaffOf(actor, session)) {
            throw new NotAllowedException("cancel another member's booking");
        }
        Association association = Lookups.association(associations, associationId);

        List<Consumer<Notifier>> notifications = new ArrayList<>();
        CancelledBooking cancelled = canceller.cancelByMember(association, session, booking.id(), notifications);
        return Outcome.of(cancelled, notifications);
    }
}
