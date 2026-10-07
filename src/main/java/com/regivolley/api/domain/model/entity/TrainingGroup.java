package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.AtLeastOneAcceptedLevelRequiredException;
import com.regivolley.api.domain.exception.InvalidCapacityException;
import com.regivolley.api.domain.exception.TrainingGroupArchivedException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.ScheduledOccurrence;
import com.regivolley.api.domain.model.valueobject.SessionGenerationPolicy;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.shared.AggregateRoot;
import com.regivolley.api.domain.shared.FieldRules;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A recurring class of an association (Turma, US-09): which levels it accepts (RN-21), where and
 * when it runs, its default capacity and its coach. It generates the {@link Session}s of the
 * coming weeks from its schedule (RN-01, US-10). Immutable: every edit returns a new instance.
 *
 * <p><b>Accepted levels (RN-21)</b> are a set of {@link LevelId}s with at least one member. The
 * default rule that a member may also book levels below their own comes from {@code LevelRank} at
 * eligibility time; it is not stored here.
 *
 * <p><b>Edits affect only sessions generated afterwards.</b> Sessions already generated keep the
 * capacity, coach and times they were created with (a session's own capacity and coach can be
 * changed on that session, RN-02); changing the schedule never moves or removes existing
 * sessions. Generation does not de-duplicate against sessions at old times, so after a schedule
 * change the admin cancels the stale sessions that no longer apply.
 *
 * <p><b>Archiving</b> retires the group: it generates nothing from then on, and every further
 * edit (including archiving again) is rejected. Whether the coach actually holds the COACH role,
 * and whether the venue belongs to the association, is checked by the use case that creates or
 * edits the group (the domain only records roles and knows the venue by id).
 */
public final class TrainingGroup implements AggregateRoot {

    public static final int MAX_NAME_LENGTH = 100;

    private final TrainingGroupId id;
    private final AssociationId associationId;
    private final String name;
    private final Set<LevelId> acceptedLevels;
    private final VenueId venueId;
    private final WeeklySchedule schedule;
    private final int defaultCapacity;
    private final MemberId coachId;
    private final TrainingGroupStatus status;

    private TrainingGroup(TrainingGroupId id, AssociationId associationId, String name, Set<LevelId> acceptedLevels,
                          VenueId venueId, WeeklySchedule schedule, int defaultCapacity, MemberId coachId,
                          TrainingGroupStatus status) {
        this.id = id;
        this.associationId = associationId;
        this.name = name;
        this.acceptedLevels = acceptedLevels;
        this.venueId = venueId;
        this.schedule = schedule;
        this.defaultCapacity = defaultCapacity;
        this.coachId = coachId;
        this.status = status;
    }

    /** Creates a new ACTIVE group. */
    public static TrainingGroup create(AssociationId associationId, String name, Set<LevelId> acceptedLevels,
                                       VenueId venueId, WeeklySchedule schedule, int defaultCapacity,
                                       MemberId coachId) {
        return reconstruct(TrainingGroupId.generate(), associationId, name, acceptedLevels, venueId, schedule,
                defaultCapacity, coachId, TrainingGroupStatus.ACTIVE);
    }

    /** Rebuilds a group from persisted data, re-checking its invariants. */
    public static TrainingGroup reconstruct(TrainingGroupId id, AssociationId associationId, String name,
                                            Set<LevelId> acceptedLevels, VenueId venueId, WeeklySchedule schedule,
                                            int defaultCapacity, MemberId coachId, TrainingGroupStatus status) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(venueId, "venueId must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(coachId, "coachId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        requirePositiveCapacity(defaultCapacity);
        return new TrainingGroup(id, associationId, FieldRules.requiredText("group name", name, MAX_NAME_LENGTH),
                requireLevels(acceptedLevels), venueId, schedule, defaultCapacity, coachId, status);
    }

    // ------------------------------------------------------------------ edits

    public TrainingGroup rename(String newName) {
        requireActive();
        return reconstruct(id, associationId, newName, acceptedLevels, venueId, schedule, defaultCapacity, coachId, status);
    }

    public TrainingGroup changeAcceptedLevels(Set<LevelId> newLevels) {
        requireActive();
        return reconstruct(id, associationId, name, newLevels, venueId, schedule, defaultCapacity, coachId, status);
    }

    public TrainingGroup changeCapacity(int newCapacity) {
        requireActive();
        return reconstruct(id, associationId, name, acceptedLevels, venueId, schedule, newCapacity, coachId, status);
    }

    public TrainingGroup changeCoach(MemberId newCoachId) {
        requireActive();
        return reconstruct(id, associationId, name, acceptedLevels, venueId, schedule, defaultCapacity, newCoachId, status);
    }

    public TrainingGroup changeSchedule(WeeklySchedule newSchedule) {
        requireActive();
        return reconstruct(id, associationId, name, acceptedLevels, venueId, newSchedule, defaultCapacity, coachId, status);
    }

    /** Retires the group: it stops generating sessions. Existing sessions are untouched. */
    public TrainingGroup archive() {
        requireActive();
        return reconstruct(id, associationId, name, acceptedLevels, venueId, schedule, defaultCapacity, coachId,
                TrainingGroupStatus.ARCHIVED);
    }

    // ------------------------------------------------------------- generation

    /**
     * New sessions for the window {@code [from, from + policy.windowWeeks())} (RN-01, US-10),
     * oldest first. Pure: it neither saves nor reads anything; the caller loads
     * {@code existingSessions} and persists the result.
     *
     * <p>A session is identified by (training group, start instant): an occurrence whose start
     * matches an existing session of this group is skipped, whatever that session's status, so a
     * cancelled session is never resurrected and re-running generation returns nothing new. Two
     * slots that resolve to the same start instant (possible on the spring-forward night) yield
     * a single session. The caller must therefore pass <b>all</b> sessions of this group that
     * fall in the window, CANCELLED ones included; sessions of other groups are ignored. Each new session inherits this group's capacity and coach (RN-02) and
     * starts at the Lisbon wall-clock time of its slot (see {@code WeeklySlot} for DST gaps and
     * overlaps). An archived group generates nothing.
     *
     * @param from            inclusive start of the window; a slot exactly at {@code from} is generated, one exactly at the window end is not
     * @param existingSessions sessions already stored, all of this association (another association's session is an
     *                         {@link IllegalArgumentException}: it signals a tenant mix-up in the caller)
     */
    public List<Session> generateSessions(Instant from, SessionGenerationPolicy policy,
                                          Collection<Session> existingSessions) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(existingSessions, "existingSessions must not be null");
        if (!isActive()) {
            return List.of();
        }
        if (existingSessions.stream().anyMatch(s -> !s.associationId().equals(associationId))) {
            throw new IllegalArgumentException("Existing sessions must all belong to the group's association");
        }
        Set<Instant> seenStarts = existingSessions.stream()
                .filter(s -> s.trainingGroupId().equals(id))
                .map(Session::startsAt)
                .collect(Collectors.toCollection(HashSet::new));
        return schedule.occurrencesBetween(from, policy.windowEnd(from)).stream()
                .filter(o -> seenStarts.add(o.startsAt()))
                .map(this::newSession)
                .toList();
    }

    private Session newSession(ScheduledOccurrence occurrence) {
        return Session.create(associationId, id, coachId, occurrence.startsAt(), occurrence.endsAt(), defaultCapacity);
    }

    // -------------------------------------------------------------- invariants

    private void requireActive() {
        if (!isActive()) {
            throw new TrainingGroupArchivedException(id);
        }
    }

    private static Set<LevelId> requireLevels(Set<LevelId> levels) {
        Objects.requireNonNull(levels, "acceptedLevels must not be null");
        if (levels.isEmpty()) {
            throw new AtLeastOneAcceptedLevelRequiredException();
        }
        return Set.copyOf(levels);
    }

    private static void requirePositiveCapacity(int capacity) {
        if (capacity <= 0) {
            throw new InvalidCapacityException(capacity);
        }
    }

    // --------------------------------------------------------------- accessors

    public TrainingGroupId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public String name() {
        return name;
    }

    public Set<LevelId> acceptedLevels() {
        return acceptedLevels;
    }

    public VenueId venueId() {
        return venueId;
    }

    public WeeklySchedule schedule() {
        return schedule;
    }

    /** Capacity inherited by every session generated from now on (RN-02). */
    public int defaultCapacity() {
        return defaultCapacity;
    }

    public MemberId coachId() {
        return coachId;
    }

    public TrainingGroupStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == TrainingGroupStatus.ACTIVE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrainingGroup other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TrainingGroup{id=%s, status=%s}".formatted(id, status);
    }
}
