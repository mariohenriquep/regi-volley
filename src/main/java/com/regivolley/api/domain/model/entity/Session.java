package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.AttendanceAlreadyMarkedException;
import com.regivolley.api.domain.exception.BookingNotFoundException;
import com.regivolley.api.domain.exception.BookingOverlapException;
import com.regivolley.api.domain.exception.BookingWindowClosedException;
import com.regivolley.api.domain.exception.CancellationClosedException;
import com.regivolley.api.domain.exception.CancellationReasonRequiredException;
import com.regivolley.api.domain.exception.CapacityBelowConfirmedException;
import com.regivolley.api.domain.exception.CoachCannotBookOwnSessionException;
import com.regivolley.api.domain.exception.DuplicateBookingException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.InvalidSessionException;
import com.regivolley.api.domain.exception.InvalidSessionStatusTransitionException;
import com.regivolley.api.domain.exception.SessionAlreadyStartedException;
import com.regivolley.api.domain.exception.SessionNotScheduledException;
import com.regivolley.api.domain.exception.SessionNotStartedException;
import com.regivolley.api.domain.model.result.BookingCancellation;
import com.regivolley.api.domain.model.result.BookingResult;
import com.regivolley.api.domain.model.result.CapacityChange;
import com.regivolley.api.domain.model.result.WaitlistPromotion;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingPolicy;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.shared.AggregateRoot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Session aggregate root. It owns its {@link Booking}s so that capacity, the waitlist and
 * "one active booking per member" are enforced in a single place (architecture.md section 10 puts
 * the optimistic lock on this aggregate).
 *
 * <p>Immutable: every operation returns a new {@code Session} (wrapped in a result record when
 * the caller also needs the affected bookings). A session is created and reconstituted only by
 * {@code SessionFactory}; the root itself creates its bookings ({@link #book}), because it guards its parts.
 *
 * <p><b>Coach (RN-02, decided 7/10/2026):</b> the session's coach never takes a seat, even when
 * playing. The coach is implicitly present and is rejected if they try to book; no booking is
 * ever created for them, so all capacity maths count only real bookings.
 *
 * <p><b>Promotion eligibility seam (RN-09):</b> promoting a waitlisted member requires that they
 * "still have balance", which depends on {@code Subscription} (a later slice). Operations that can
 * free seats take a {@code Predicate<MemberId>}; waitlisted members it rejects are skipped (they
 * stay WAITLISTED, keeping their place) and the next in arrival order is tried instead.
 */
public final class Session implements AggregateRoot {

    private static final Map<SessionStatus, Set<SessionStatus>> ALLOWED_TRANSITIONS = Map.of(
            SessionStatus.SCHEDULED, EnumSet.of(SessionStatus.COMPLETED, SessionStatus.CANCELLED),
            SessionStatus.COMPLETED, EnumSet.noneOf(SessionStatus.class),
            SessionStatus.CANCELLED, EnumSet.noneOf(SessionStatus.class)
    );

    private static final Comparator<Booking> FIFO = Comparator.comparing(Booking::requestedAt);

    private final SessionId id;
    private final AssociationId associationId;
    private final TrainingGroupId trainingGroupId;
    private final MemberId coachId;
    private final Instant startsAt;
    private final Instant endsAt;
    private final int capacity;
    private final SessionStatus status;
    private final String cancellationReason;
    private final List<Booking> bookings;
    private final long version;

    /**
     * Checks every invariant, so no session exists in an invalid state. Public because the only callers are
     * {@code SessionFactory} (new sessions and persisted ones) and this class; the architecture test pins that.
     * Every operation below builds its result through here too, so the rules hold after each transition.
     */
    public Session(SessionId id, AssociationId associationId, TrainingGroupId trainingGroupId,
                   MemberId coachId, Instant startsAt, Instant endsAt, int capacity,
                   SessionStatus status, String cancellationReason, List<Booking> bookings,
                   long version) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(trainingGroupId, "trainingGroupId must not be null");
        Objects.requireNonNull(coachId, "coachId must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        List<Booking> owned = List.copyOf(Objects.requireNonNull(bookings, "bookings must not be null"));

        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidSessionException("Session end must be after its start");
        }
        requirePositiveCapacity(capacity);
        if ((status == SessionStatus.CANCELLED) != (cancellationReason != null)) {
            throw new InvalidSessionException("A cancellation reason is required exactly when the session is CANCELLED");
        }
        if (owned.stream().anyMatch(b -> !b.sessionId().equals(id) || !b.associationId().equals(associationId))) {
            throw new InvalidSessionException("Every booking must belong to this session and association");
        }
        if (countConfirmed(owned) > capacity) {
            throw new InvalidSessionException("Confirmed bookings exceed the session capacity");
        }
        requireOneLiveBookingPerMember(owned, coachId);
        this.id = id;
        this.associationId = associationId;
        this.trainingGroupId = trainingGroupId;
        this.coachId = coachId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
        this.status = status;
        this.cancellationReason = cancellationReason;
        this.bookings = owned;
        this.version = version;
    }

    private static void requireOneLiveBookingPerMember(List<Booking> bookings, MemberId coachId) {
        Set<MemberId> seen = new HashSet<>();
        for (Booking booking : bookings) {
            if (booking.status() == BookingStatus.CANCELLED) {
                continue;
            }
            if (booking.memberId().equals(coachId)) {
                throw new InvalidSessionException("The session coach cannot have a booking in their own session");
            }
            if (!seen.add(booking.memberId())) {
                throw new InvalidSessionException("A member can have at most one non-cancelled booking per session");
            }
        }
    }

    // ---------------------------------------------------------------- booking

    /**
     * Requests a place (RN-08): CONFIRMED when a seat is free, otherwise WAITLISTED in arrival
     * order. A seat that is free while only ineligible members wait is taken by the new request
     * (RN-09 note). Rejected when the session isn't SCHEDULED, the member is the session's coach
     * (RN-02), the booking window is closed (RN-03) or the member already has a non-cancelled
     * booking here (RN-07; after cancelling, booking again is allowed and joins the back of the queue).
     * Whether the member is allowed to book at all (level, plan, balance - RN-06) is decided
     * elsewhere.
     */
    public BookingResult book(MemberId memberId, BookingPolicy policy, Clock clock) {
        Objects.requireNonNull(memberId, "memberId must not be null");
        requireScheduled();
        if (memberId.equals(coachId)) {
            throw new CoachCannotBookOwnSessionException();
        }
        Instant now = clock.instant();
        Instant opensAt = policy.bookingOpensAt(startsAt);
        if (now.isBefore(opensAt)) {
            throw BookingWindowClosedException.notYetOpen(opensAt);
        }
        if (!now.isBefore(startsAt)) {
            throw BookingWindowClosedException.alreadyClosed(startsAt);
        }
        if (hasLiveBooking(memberId)) {
            throw new DuplicateBookingException();
        }

        BookingStatus initial = freeSeats() > 0 ? BookingStatus.CONFIRMED : BookingStatus.WAITLISTED;
        Instant confirmedAt = initial == BookingStatus.CONFIRMED ? now : null;
        Booking booking = new Booking(BookingId.generate(), associationId, id, memberId, initial, now, confirmedAt, null);
        List<Booking> updated = new ArrayList<>(bookings);
        updated.add(booking);
        return new BookingResult(withBookings(updated), booking);
    }

    /**
     * Cancels a booking (RN-10) and promotes the waitlist into the freed seat (RN-09).
     *
     * <p>Cancelling at or after the session start is rejected. A CONFIRMED booking cancelled after {@link BookingPolicy#freeCancellationDeadline} (exactly
     * at the deadline is still free) is recorded as {@link CancellationKind#LATE}; the seat is
     * freed all the same. A WAITLISTED booking holds no seat or credit, so cancelling it is always
     * FREE.
     *
     * @param eligibleForPromotion RN-09 seam: whether a waitlisted member may be promoted (balance)
     */
    public BookingCancellation cancelBooking(BookingId bookingId, BookingPolicy policy, Clock clock,
                                             Predicate<MemberId> eligibleForPromotion) {
        Objects.requireNonNull(eligibleForPromotion, "eligibleForPromotion must not be null");
        requireCancellationsOpen(clock);
        Instant now = clock.instant();
        Booking booking = findExistingBooking(bookingId);
        boolean late = booking.status() == BookingStatus.CONFIRMED
                && now.isAfter(policy.freeCancellationDeadline(startsAt));
        return cancelAndPromote(booking, late ? CancellationKind.LATE : CancellationKind.FREE, now, eligibleForPromotion);
    }

    /**
     * Cancels a booking because the association decided so (deactivating the member, US-08), not the
     * member: there is no late window, so the cancellation is {@link CancellationKind#BY_ASSOCIATION}
     * whenever it is made and the credit of a booking that held a seat is refundable. The seat is freed
     * and the waitlist promoted exactly as in {@link #cancelBooking}. Like it, rejected at or after the
     * start and when the session is not SCHEDULED; the booking must still be WAITLISTED or CONFIRMED.
     */
    public BookingCancellation cancelBookingByAssociation(BookingId bookingId, Clock clock,
                                                          Predicate<MemberId> eligibleForPromotion) {
        Objects.requireNonNull(eligibleForPromotion, "eligibleForPromotion must not be null");
        requireCancellationsOpen(clock);
        return cancelAndPromote(findExistingBooking(bookingId), CancellationKind.BY_ASSOCIATION, clock.instant(),
                eligibleForPromotion);
    }

    /** Whether bookings can still be cancelled: the session is SCHEDULED and has not started (RN-10). */
    public boolean acceptsCancellations(Clock clock) {
        return status == SessionStatus.SCHEDULED && clock.instant().isBefore(startsAt);
    }

    private void requireCancellationsOpen(Clock clock) {
        requireScheduled();
        if (!clock.instant().isBefore(startsAt)) {
            throw new CancellationClosedException(startsAt);
        }
    }

    private BookingCancellation cancelAndPromote(Booking booking, CancellationKind kind, Instant now,
                                                 Predicate<MemberId> eligibleForPromotion) {
        Booking cancelled = booking.cancel(kind);
        Promotion promotion = promote(replace(bookings, cancelled), capacity, eligibleForPromotion, now);
        return new BookingCancellation(withBookings(promotion.bookings()), cancelled, promotion.promoted());
    }

    // --------------------------------------------------------------- capacity

    /**
     * Changes this session's capacity (RN-02, US-12). Seats stop changing at the start: at or after
     * it the change is rejected, consistent with RN-03 and RN-10. Lowering below the CONFIRMED count is
     * rejected (lowering to exactly that count is fine); raising promotes waitlisted bookings in
     * arrival order into the new seats. A multiple of 6 is only a recommendation and isn't enforced.
     */
    public CapacityChange changeCapacity(int newCapacity, Clock clock, Predicate<MemberId> eligibleForPromotion) {
        Objects.requireNonNull(eligibleForPromotion, "eligibleForPromotion must not be null");
        requireScheduled();
        Instant now = clock.instant();
        if (!now.isBefore(startsAt)) {
            throw new SessionAlreadyStartedException(startsAt);
        }
        if (newCapacity <= 0) {
            throw new InvalidCapacityException(newCapacity);
        }
        int confirmed = confirmedCount();
        if (newCapacity < confirmed) {
            throw new CapacityBelowConfirmedException(newCapacity, confirmed);
        }
        Promotion promotion = promote(bookings, newCapacity, eligibleForPromotion, now);
        Session updated = new Session(id, associationId, trainingGroupId, coachId, startsAt, endsAt, newCapacity,
                status, cancellationReason, promotion.bookings(), version);
        return new CapacityChange(updated, promotion.promoted());
    }

    /**
     * Re-evaluates the waitlist (RN-09 note): promotes into free seats, in arrival order, the
     * waitlisted members the predicate accepts. For when a waitlisted member becomes eligible
     * again (e.g. regains balance) while seats are free. Only while the session is SCHEDULED and
     * hasn't started; otherwise it is a no-op returning this session unchanged and no promotions.
     */
    public WaitlistPromotion promoteWaitlist(Clock clock, Predicate<MemberId> eligibleForPromotion) {
        Objects.requireNonNull(eligibleForPromotion, "eligibleForPromotion must not be null");
        Instant now = clock.instant();
        if (status != SessionStatus.SCHEDULED || !now.isBefore(startsAt)) {
            return new WaitlistPromotion(this, List.of());
        }
        Promotion promotion = promote(bookings, capacity, eligibleForPromotion, now);
        return new WaitlistPromotion(withBookings(promotion.bookings()), promotion.promoted());
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * SCHEDULED -> CANCELLED (RN-04, RN-05). Every active booking becomes CANCELLED with
     * {@link CancellationKind#BY_SESSION}, so a later slice can refund credits and notify
     * ({@link #bookingsToRefund()} for credits, {@link #bookingsCancelledBySession()} for
     * notifications). A booking cancelled LATE earlier stays LATE (no refund). Rejected once
     * attendance has been marked for any booking.
     */
    public Session cancel(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new CancellationReasonRequiredException();
        }
        requireTransitionTo(SessionStatus.CANCELLED);
        if (bookings.stream().anyMatch(b -> b.status() == BookingStatus.ATTENDED || b.status() == BookingStatus.NO_SHOW)) {
            throw new AttendanceAlreadyMarkedException();
        }
        List<Booking> updated = bookings.stream()
                .map(b -> b.isActive() ? b.cancel(CancellationKind.BY_SESSION) : b)
                .toList();
        return new Session(id, associationId, trainingGroupId, coachId, startsAt, endsAt, capacity,
                SessionStatus.CANCELLED, reason, updated, version);
    }

    /**
     * SCHEDULED -> COMPLETED, only at or after the start. Bookings still WAITLISTED never got a
     * seat and are cancelled as {@link CancellationKind#NOT_PROMOTED}; CONFIRMED bookings without
     * attendance marked are left as they are (not turned into NO_SHOW automatically).
     */
    public Session complete(Clock clock) {
        requireTransitionTo(SessionStatus.COMPLETED);
        requireStarted(clock);
        List<Booking> updated = bookings.stream()
                .map(b -> b.status() == BookingStatus.WAITLISTED ? b.cancel(CancellationKind.NOT_PROMOTED) : b)
                .toList();
        return new Session(id, associationId, trainingGroupId, coachId, startsAt, endsAt, capacity,
                SessionStatus.COMPLETED, null, updated, version);
    }

    // ------------------------------------------------------------ attendance

    /** Marks a CONFIRMED booking ATTENDED; only from the session start on (US-17). */
    public Session markAttended(BookingId bookingId, Clock clock) {
        requireStarted(clock);
        Booking booking = findExistingBooking(bookingId);
        return withBookings(replace(bookings, booking.markAttended()));
    }

    /**
     * Marks a CONFIRMED booking NO_SHOW; only from the session start on (US-17). This only records
     * the fact: whether no-shows block future bookings is RN-11, still an open question.
     */
    public Session markNoShow(BookingId bookingId, Clock clock) {
        requireStarted(clock);
        Booking booking = findExistingBooking(bookingId);
        return withBookings(replace(bookings, booking.markNoShow()));
    }

    // ------------------------------------------------------------------ reads

    public int confirmedCount() {
        return countConfirmed(bookings);
    }

    public int freeSeats() {
        return capacity - confirmedCount();
    }

    /** WAITLISTED bookings in arrival (FIFO) order. */
    public List<Booking> waitlist() {
        return waitlistOf(bookings);
    }

    /** 1-based position of the member in the waitlist, empty if they aren't waitlisted. */
    public OptionalInt waitlistPosition(MemberId memberId) {
        List<Booking> waitlist = waitlist();
        for (int i = 0; i < waitlist.size(); i++) {
            if (waitlist.get(i).memberId().equals(memberId)) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    /** Every booking cancelled because the whole session was cancelled (RN-04): everyone to notify, waitlisted included. */
    public List<Booking> bookingsCancelledBySession() {
        return bookings.stream()
                .filter(Booking::isCancelledBySession)
                .toList();
    }

    /**
     * Bookings cancelled by the session that held a seat, i.e. whose credit must be refunded
     * (RN-04). Waitlisted ones never consumed a credit and a LATE-cancelled one stays LATE, so
     * neither appears here.
     */
    public List<Booking> bookingsToRefund() {
        return bookingsCancelledBySession().stream().filter(Booking::consumedCredit).toList();
    }

    /** The booking with this id in this session, whatever its status. */
    public Optional<Booking> findBooking(BookingId bookingId) {
        return bookings.stream().filter(b -> b.id().equals(bookingId)).findFirst();
    }

    /**
     * RN-07: whether this session and {@code other} share any time. Back-to-back sessions (one ends
     * exactly when the next starts) do not overlap. Says nothing about bookings or associations: the
     * caller asks it about the sessions in which a member holds a live booking.
     */
    public boolean overlaps(Session other) {
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }

    /**
     * RN-07: rejects when any of {@code sessionsOfTheMember} - the sessions in which a member holds a
     * live booking, as the repository narrowed them down - takes place at the same time as this one. This
     * session itself and CANCELLED ones are ignored (a duplicate booking is {@link #book}'s rule).
     *
     * @throws BookingOverlapException naming the first overlapping session
     */
    public void requireNoOverlapWith(Collection<Session> sessionsOfTheMember) {
        sessionsOfTheMember.stream()
                .filter(other -> !other.id.equals(id) && other.status != SessionStatus.CANCELLED)
                .filter(this::overlaps)
                .findFirst()
                .ifPresent(other -> {
                    throw new BookingOverlapException(other.id, other.startsAt);
                });
    }

    /** The member's WAITLISTED or CONFIRMED booking in this session, if they have one. */
    public Optional<Booking> activeBookingOf(MemberId memberId) {
        return bookings.stream().filter(b -> b.isActive() && b.memberId().equals(memberId)).findFirst();
    }

    public Instant bookingOpensAt(BookingPolicy policy) {
        return policy.bookingOpensAt(startsAt);
    }

    // ---------------------------------------------------------------- helpers

    /** RN-07: any non-cancelled booking (WAITLISTED, CONFIRMED, ATTENDED, NO_SHOW) blocks a new one. */
    private boolean hasLiveBooking(MemberId memberId) {
        return bookings.stream()
                .anyMatch(b -> b.status() != BookingStatus.CANCELLED && b.memberId().equals(memberId));
    }

    private Booking findExistingBooking(BookingId bookingId) {
        return findBooking(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private void requireScheduled() {
        if (status != SessionStatus.SCHEDULED) {
            throw new SessionNotScheduledException(status);
        }
    }

    private void requireTransitionTo(SessionStatus target) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(target)) {
            throw new InvalidSessionStatusTransitionException(status, target);
        }
    }

    private void requireStarted(Clock clock) {
        if (clock.instant().isBefore(startsAt)) {
            throw new SessionNotStartedException(startsAt);
        }
    }

    private static void requirePositiveCapacity(int capacity) {
        if (capacity <= 0) {
            throw new InvalidSessionException("Session capacity must be greater than zero");
        }
    }

    private Session withBookings(List<Booking> newBookings) {
        return new Session(id, associationId, trainingGroupId, coachId, startsAt, endsAt, capacity, status,
                cancellationReason, List.copyOf(newBookings), version);
    }

    private static int countConfirmed(List<Booking> bookings) {
        return (int) bookings.stream().filter(b -> b.status() == BookingStatus.CONFIRMED).count();
    }

    private static List<Booking> waitlistOf(List<Booking> bookings) {
        // List.sort is stable, so equal requestedAt keeps insertion order.
        return bookings.stream()
                .filter(b -> b.status() == BookingStatus.WAITLISTED)
                .sorted(FIFO)
                .toList();
    }

    private static List<Booking> replace(List<Booking> bookings, Booking updated) {
        List<Booking> result = new ArrayList<>(bookings);
        result.set(result.indexOf(updated), updated);
        return result;
    }

    private record Promotion(List<Booking> bookings, List<Booking> promoted) {
    }

    /** Fills free seats from the waitlist in FIFO order, skipping members the predicate rejects (RN-09). */
    private static Promotion promote(List<Booking> bookings, int capacity, Predicate<MemberId> eligible,
                                     Instant now) {
        int free = capacity - countConfirmed(bookings);
        List<Booking> result = new ArrayList<>(bookings);
        List<Booking> promoted = new ArrayList<>();
        for (Booking waiting : waitlistOf(bookings)) {
            if (free == 0) {
                break;
            }
            if (eligible.test(waiting.memberId())) {
                Booking confirmed = waiting.confirm(now);
                result = replace(result, confirmed);
                promoted.add(confirmed);
                free--;
            }
        }
        return new Promotion(result, promoted);
    }

    // --------------------------------------------------------------- accessors

    public SessionId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public TrainingGroupId trainingGroupId() {
        return trainingGroupId;
    }

    public MemberId coachId() {
        return coachId;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public int capacity() {
        return capacity;
    }

    public SessionStatus status() {
        return status;
    }

    /** Optimistic-lock version (architecture.md section 10): 0 when created, carried unchanged by every operation; persistence increments it. */
    public long version() {
        return version;
    }

    /** Present only when the session is CANCELLED. */
    public Optional<String> cancellationReason() {
        return Optional.ofNullable(cancellationReason);
    }

    /** All bookings in the order they were requested, whatever their status. */
    public List<Booking> bookings() {
        return bookings;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Session other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Session{id=%s, startsAt=%s, status=%s}".formatted(id, startsAt, status);
    }
}
