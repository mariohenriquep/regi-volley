package com.regivolley.api.application.usecase;

import com.regivolley.api.application.command.BookSessionCommand;
import com.regivolley.api.application.port.TransactionRunner;
import com.regivolley.api.application.result.PlacedBooking;
import com.regivolley.api.domain.exception.MemberNotFoundException;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Member;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.Subscription;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.result.BookingResult;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.repository.AssociationRepository;
import com.regivolley.api.domain.repository.MemberRepository;
import com.regivolley.api.domain.repository.SessionRepository;
import com.regivolley.api.domain.repository.SubscriptionRepository;
import com.regivolley.api.domain.repository.TrainingGroupRepository;
import com.regivolley.api.domain.service.BookingEligibility;
import com.regivolley.api.domain.service.BookingTarget;
import com.regivolley.api.domain.service.EligibilityDecision;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.OptionalInt;

/**
 * US-14. Loads the facts, asks the domain, stores the outcome (architecture.md section 5): can this
 * member book (RN-06, RN-14, RN-18, RN-21: {@link BookingEligibility}), is another of their sessions
 * in the way (RN-07), then {@link Session#book} decides CONFIRMED or WAITLISTED (RN-08). Only a
 * confirmed booking is charged, on the subscription eligibility chose (RN-15), counting the weekly
 * allowance across all the member's subscriptions (RN-16).
 *
 * <p>Runs in a new transaction per attempt and retries lost races (section 10): the session and the
 * subscription are saved together, so a conflict on either leaves neither stored and the next
 * attempt decides again on the winner's state.
 */
@Service
public class BookSessionService implements BookSessionUseCase {

    private final SessionRepository sessions;
    private final MemberRepository members;
    private final AssociationRepository associations;
    private final TrainingGroupRepository groups;
    private final SubscriptionRepository subscriptions;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public BookSessionService(SessionRepository sessions, MemberRepository members, AssociationRepository associations,
                              TrainingGroupRepository groups, SubscriptionRepository subscriptions,
                              TransactionRunner transactions, Clock clock) {
        this.sessions = sessions;
        this.members = members;
        this.associations = associations;
        this.groups = groups;
        this.subscriptions = subscriptions;
        this.unitOfWork = new UnitOfWork(transactions);
        this.clock = clock;
    }

    @Override
    public PlacedBooking execute(BookSessionCommand command) {
        return unitOfWork.retrying(() -> attempt(command));
    }

    private PlacedBooking attempt(BookSessionCommand command) {
        var associationId = command.actor().associationId();
        var memberId = command.actor().memberId();
        // First thing in the transaction: the member's row lock queues this member's concurrent bookings, so the
        // overlap check below sees what the others committed (RN-07) and their credit is charged one at a time.
        Member member = members.findByIdForUpdate(associationId, memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
        Session session = Lookups.session(sessions, associationId, command.sessionId());
        Association association = Lookups.association(associations, associationId);
        TrainingGroup group = Lookups.group(groups, associationId, session.trainingGroupId());
        List<Subscription> held = subscriptions.findByMember(associationId, memberId);

        BookingTarget target = BookingTarget.of(session, association.ranksOf(group.acceptedLevels()));
        EligibilityDecision decision = BookingEligibility.evaluate(member.bookingProfile(association, held), target);
        Subscription toCharge = decision.requireEligible();
        requireNoOverlap(session, member);

        BookingResult booked = session.book(memberId, association.bookingPolicy(), clock);
        Session stored = sessions.save(booked.session());
        Booking booking = booked.booking();
        if (booking.status() == BookingStatus.CONFIRMED) {
            subscriptions.save(toCharge.consume(booking.id(), session.startsAt(), held));
        }
        return new PlacedBooking(stored, booking, waitlistPositionOf(stored, booking));
    }

    /** RN-07: the repository narrows the window to the member's live bookings, the session decides what overlaps. */
    private void requireNoOverlap(Session session, Member member) {
        session.requireNoOverlapWith(sessions.findWithLiveBookingOverlapping(session.associationId(), member.id(),
                session.startsAt(), session.endsAt()));
    }

    private static OptionalInt waitlistPositionOf(Session stored, Booking booking) {
        return booking.status() == BookingStatus.WAITLISTED ? stored.waitlistPosition(booking.memberId()) : OptionalInt.empty();
    }
}
