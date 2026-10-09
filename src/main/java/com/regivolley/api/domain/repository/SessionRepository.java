package com.regivolley.api.domain.repository;

import com.regivolley.api.domain.exception.SessionModifiedConcurrentlyException;
import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.SessionId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port for {@link Session} aggregates, bookings included (there is no booking repository). Every
 * read is scoped to one association (architecture.md section 8); every returned session carries
 * all its bookings, the waitlist in arrival order (requested_at, then id).
 *
 * <p>Time ranges select sessions by <em>start</em> instant and are half-open: {@code from} is
 * included, {@code toExclusive} is not. All statuses are returned, CANCELLED included; callers
 * filter what they need.
 */
public interface SessionRepository {

    Optional<Session> findById(AssociationId associationId, SessionId id);

    /** Sessions of the association starting in {@code [from, toExclusive)}, oldest first (week view, US-13). */
    List<Session> findStartingBetween(AssociationId associationId, Instant from, Instant toExclusive);

    /**
     * Sessions of one training group starting in {@code [from, toExclusive)}, CANCELLED ones
     * included, oldest first: exactly what {@code TrainingGroup.occurrencesToGenerate} must be given.
     */
    List<Session> findByTrainingGroupStartingBetween(AssociationId associationId, TrainingGroupId trainingGroupId,
                                                     Instant from, Instant toExclusive);

    /**
     * Sessions in which the member has a non-cancelled booking (CONFIRMED, WAITLISTED, ATTENDED or
     * NO_SHOW) and that overlap the interval, i.e. start before {@code toExclusive} and end after
     * {@code from} (RN-07 overlap check), oldest first.
     */
    List<Session> findWithLiveBookingOverlapping(AssociationId associationId, MemberId memberId,
                                                 Instant from, Instant toExclusive);

    /**
     * Inserts a new session or updates an existing one together with its bookings, and returns it
     * as stored, with its new version. Always moves the stored version forward by one, even when
     * only bookings changed, so two concurrent bookings of the last seat cannot both succeed. Keep
     * working with the returned instance: the one passed in is now stale.
     *
     * @throws SessionModifiedConcurrentlyException if the stored session is no longer at the
     *         version of {@code session} (someone saved in between), if it has a version but no stored
     *         row in its association, if its row could not be locked in time, or if another run already
     *         stored the same live booking or the same generated occurrence (same group and start);
     *         nothing is written. Retry in a new transaction.
     */
    Session save(Session session);
}
