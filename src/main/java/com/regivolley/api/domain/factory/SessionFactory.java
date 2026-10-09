package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.model.entity.Booking;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.BookingId;
import com.regivolley.api.domain.model.valueobject.BookingStatus;
import com.regivolley.api.domain.model.valueobject.CancellationKind;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.ScheduledOccurrence;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.SessionStatus;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Creates and reconstitutes {@link Session}s and their {@link Booking}s: the only place a session is born.
 * {@link Session}'s constructor checks every invariant, so no path can produce an invalid session. Stateless, so its
 * methods are static.
 *
 * <p>A <em>new</em> booking is not made here: the session creates its own bookings ({@code Session.book}), because
 * capacity, the waitlist and the booking window are the session's rules. A booking loaded from storage comes back with
 * its session, through {@link #reconstituteBooking}.
 */
public final class SessionFactory {

    private SessionFactory() {
    }

    /**
     * A new SCHEDULED session with no bookings, a generated id, at version 0. Capacity is typically inherited from the
     * training group (RN-02).
     */
    public static Session create(AssociationId associationId, TrainingGroupId trainingGroupId, MemberId coachId,
                                 Instant startsAt, Instant endsAt, int capacity) {
        return new Session(SessionId.generate(), associationId, trainingGroupId, coachId, startsAt, endsAt, capacity,
                SessionStatus.SCHEDULED, null, List.of(), 0L);
    }

    /**
     * The sessions a group still needs for the window {@code [from, from + policy.windowWeeks())} (RN-01, US-10),
     * oldest first. The group decides which occurrences are missing (an archived group has none; one that already has a
     * session at that start, whatever its status, is skipped, so a cancelled session is never resurrected and running
     * generation twice returns nothing new); this builds one session per occurrence, inheriting the group's capacity
     * and coach (RN-02). Pure: it neither saves nor reads anything.
     *
     * @param existingSessions all the group's sessions in the window, CANCELLED ones included (see
     *                         {@link TrainingGroup#occurrencesToGenerate})
     * @throws IllegalArgumentException if a session of another association is among them
     */
    public static List<Session> createSessionsFor(TrainingGroup group, Instant from, SessionGenerationPolicy policy,
                                                  Collection<Session> existingSessions) {
        Objects.requireNonNull(group, "group must not be null");
        return group.occurrencesToGenerate(from, policy, existingSessions).stream()
                .map(occurrence -> createFor(group, occurrence))
                .toList();
    }

    private static Session createFor(TrainingGroup group, ScheduledOccurrence occurrence) {
        return create(group.associationId(), group.id(), group.coachId(), occurrence.startsAt(), occurrence.endsAt(),
                group.defaultCapacity());
    }

    /**
     * Rebuilds a session from persisted data.
     *
     * @param bookings the session's bookings, themselves reconstituted with {@link #reconstituteBooking}
     * @param version  the optimistic-lock version it was loaded with (architecture.md section 10)
     */
    public static Session reconstitute(SessionId id, AssociationId associationId, TrainingGroupId trainingGroupId,
                                       MemberId coachId, Instant startsAt, Instant endsAt, int capacity,
                                       SessionStatus status, String cancellationReason, List<Booking> bookings,
                                       long version) {
        return new Session(id, associationId, trainingGroupId, coachId, startsAt, endsAt, capacity, status,
                cancellationReason, bookings, version);
    }

    /** Rebuilds a booking from persisted data, to be handed to {@link #reconstitute} with the rest of its session. */
    public static Booking reconstituteBooking(BookingId id, AssociationId associationId, SessionId sessionId,
                                              MemberId memberId, BookingStatus status, Instant requestedAt,
                                              Instant confirmedAt, CancellationKind cancellationKind) {
        return new Booking(id, associationId, sessionId, memberId, status, requestedAt, confirmedAt, cancellationKind);
    }
}
