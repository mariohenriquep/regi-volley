package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidBookingException;
import com.regivolley.api.domain.exception.InvalidBookingStatusTransitionException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.shared.Entity;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A member's place in a {@link Session}: either a seat (CONFIRMED) or a waitlist slot
 * (WAITLISTED), later ATTENDED / NO_SHOW or CANCELLED (RN-12).
 *
 * <p>Immutable: every transition returns a new instance. A booking is only created and moved by
 * its {@code Session} (creation and every transition are package-private), which enforces
 * capacity, the booking window, one-live-booking-per-member and the cancellation rules; the
 * transitions here only guard the state machine itself.
 *
 * <p>{@code confirmedAt} records whether the booking ever held a seat. It survives cancellation,
 * so a later slice can tell a cancelled booking that consumed a credit from one that never did.
 */
public final class Booking implements Entity {

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
            BookingStatus.WAITLISTED, EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
            BookingStatus.CONFIRMED, EnumSet.of(BookingStatus.ATTENDED, BookingStatus.NO_SHOW, BookingStatus.CANCELLED),
            BookingStatus.ATTENDED, EnumSet.noneOf(BookingStatus.class),
            BookingStatus.NO_SHOW, EnumSet.noneOf(BookingStatus.class),
            BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class)
    );

    private final BookingId id;
    private final AssociationId associationId;
    private final SessionId sessionId;
    private final MemberId memberId;
    private final BookingStatus status;
    private final Instant requestedAt;
    private final Instant confirmedAt;
    private final CancellationKind cancellationKind;

    private Booking(BookingId id, AssociationId associationId, SessionId sessionId, MemberId memberId,
                    BookingStatus status, Instant requestedAt, Instant confirmedAt,
                    CancellationKind cancellationKind) {
        this.id = id;
        this.associationId = associationId;
        this.sessionId = sessionId;
        this.memberId = memberId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.confirmedAt = confirmedAt;
        this.cancellationKind = cancellationKind;
    }

    /**
     * New booking request, only created through {@link Session#book}. The initial status is
     * CONFIRMED (a seat was free, so it is confirmed right at the request) or WAITLISTED (RN-08).
     */
    static Booking create(AssociationId associationId, SessionId sessionId, MemberId memberId,
                          BookingStatus initialStatus, Instant requestedAt) {
        if (initialStatus != BookingStatus.CONFIRMED && initialStatus != BookingStatus.WAITLISTED) {
            throw new InvalidBookingException("A new booking must start CONFIRMED or WAITLISTED, not " + initialStatus);
        }
        Instant confirmedAt = initialStatus == BookingStatus.CONFIRMED ? requestedAt : null;
        return reconstruct(BookingId.generate(), associationId, sessionId, memberId, initialStatus, requestedAt,
                confirmedAt, null);
    }

    /** Rebuilds a booking from persisted data, re-checking its invariants. */
    public static Booking reconstruct(BookingId id, AssociationId associationId, SessionId sessionId,
                                      MemberId memberId, BookingStatus status, Instant requestedAt,
                                      Instant confirmedAt, CancellationKind cancellationKind) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        if ((status == BookingStatus.CANCELLED) != (cancellationKind != null)) {
            throw new InvalidBookingException("A cancellation kind is required exactly when the booking is CANCELLED");
        }
        if (confirmedAt != null && confirmedAt.isBefore(requestedAt)) {
            throw new InvalidBookingException("A booking cannot be confirmed before it was requested");
        }
        requireConfirmationConsistentWith(status, confirmedAt, cancellationKind);
        return new Booking(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(associationId, "associationId must not be null"),
                Objects.requireNonNull(sessionId, "sessionId must not be null"),
                Objects.requireNonNull(memberId, "memberId must not be null"),
                status, requestedAt, confirmedAt, cancellationKind
        );
    }

    private static void requireConfirmationConsistentWith(BookingStatus status, Instant confirmedAt,
                                                          CancellationKind kind) {
        boolean mustHaveBeenConfirmed = status == BookingStatus.CONFIRMED || status == BookingStatus.ATTENDED
                || status == BookingStatus.NO_SHOW || kind == CancellationKind.LATE;
        boolean mustNeverHaveBeenConfirmed = status == BookingStatus.WAITLISTED
                || kind == CancellationKind.NOT_PROMOTED;
        if (mustHaveBeenConfirmed && confirmedAt == null) {
            throw new InvalidBookingException("A " + (kind == null ? status : kind) + " booking must have been confirmed");
        }
        if (mustNeverHaveBeenConfirmed && confirmedAt != null) {
            throw new InvalidBookingException("A " + (kind == null ? status : kind) + " booking cannot have been confirmed");
        }
    }

    /** WAITLISTED -> CONFIRMED at {@code at}: a seat was assigned (RN-09, US-12). */
    Booking confirm(Instant at) {
        Objects.requireNonNull(at, "at must not be null");
        return transitionTo(BookingStatus.CONFIRMED, at, null);
    }

    /** CONFIRMED -> ATTENDED. */
    Booking markAttended() {
        return transitionTo(BookingStatus.ATTENDED, confirmedAt, null);
    }

    /** CONFIRMED -> NO_SHOW. */
    Booking markNoShow() {
        return transitionTo(BookingStatus.NO_SHOW, confirmedAt, null);
    }

    /** WAITLISTED | CONFIRMED -> CANCELLED, recording why. */
    Booking cancel(CancellationKind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        return transitionTo(BookingStatus.CANCELLED, confirmedAt, kind);
    }

    private Booking transitionTo(BookingStatus target, Instant newConfirmedAt, CancellationKind kind) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(target)) {
            throw new InvalidBookingStatusTransitionException(status, target);
        }
        return reconstruct(id, associationId, sessionId, memberId, target, requestedAt, newConfirmedAt, kind);
    }

    /** WAITLISTED or CONFIRMED: the booking still occupies a seat or a waitlist slot. */
    public boolean isActive() {
        return status.isActive();
    }

    /** True if the booking ever held a seat, even if it has since been cancelled (a credit was consumed - RN-06). */
    public boolean consumedCredit() {
        return confirmedAt != null;
    }

    public boolean isCancelledBySession() {
        return cancellationKind == CancellationKind.BY_SESSION;
    }

    /**
     * Whether the credit consumed by this booking goes back to the member: it held a seat and was
     * cancelled for free (RN-10) or by the session (RN-04). A LATE cancellation keeps it consumed.
     * Free cancellations are refunded when they happen, session cancellations through
     * {@link Session#bookingsToRefund()}.
     */
    public boolean creditRefundable() {
        return consumedCredit()
                && (cancellationKind == CancellationKind.FREE || cancellationKind == CancellationKind.BY_SESSION);
    }

    public BookingId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public MemberId memberId() {
        return memberId;
    }

    public BookingStatus status() {
        return status;
    }

    /** When the member asked for the place; the waitlist is ordered by it (RN-08). */
    public Instant requestedAt() {
        return requestedAt;
    }

    /** When the booking got a seat; empty if it never did (waitlisted, or cancelled while waitlisted). */
    public Optional<Instant> confirmedAt() {
        return Optional.ofNullable(confirmedAt);
    }

    /** Present only when the booking is CANCELLED. */
    public Optional<CancellationKind> cancellationKind() {
        return Optional.ofNullable(cancellationKind);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Booking{id=%s, sessionId=%s, status=%s}".formatted(id, sessionId, status);
    }
}
