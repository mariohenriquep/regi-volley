package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidMemberException;
import com.regivolley.api.domain.exception.InvalidMemberStatusTransitionException;
import com.regivolley.api.domain.exception.LastRoleCannotBeRevokedException;
import com.regivolley.api.domain.exception.MemberAnonymisedException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.LevelChange;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.MemberStatus;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.service.MemberBookingProfile;
import com.regivolley.api.domain.shared.AggregateRoot;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A person inside one association (US-05..US-08, RN-20): contact data, roles, status, current
 * {@link Level} and the history of who changed that level. Immutable: every change returns a new
 * instance. It holds only what the requirements ask for (name, email, phone, RGPD consent).
 *
 * <p><b>Deactivation (US-08)</b> only marks the member INACTIVE, which makes booking eligibility
 * reject them (RN-06). Cancelling their future bookings is the use case's job (issue #15): it
 * has to load the sessions, which a member does not know. The history is kept.
 *
 * <p><b>Roles</b> are recorded here but never checked here: who may do what is enforced by the
 * infrastructure before a use case runs (architecture.md section 11), and which level changes a
 * coach may make is likewise a use case concern.
 *
 * <p><b>Anonymisation (RGPD erasure)</b> replaces the name, email and phone with placeholders,
 * deactivates the member and reduces their roles to MEMBER, keeping the id, the level history and
 * the consent record, so attendance and payment history stay consistent. It is irreversible: an
 * anonymised member can no longer be changed ({@link MemberAnonymisedException}). It erases only
 * this aggregate. A full erasure also has to anonymise the person's {@link JoinRequest}s and
 * anything else holding their data, which is the erasure use case's job.
 *
 * <p>{@link #toString()} prints ids only.
 */
public final class Member implements AggregateRoot {

    private static final Map<MemberStatus, Set<MemberStatus>> ALLOWED_TRANSITIONS = Map.of(
            MemberStatus.ACTIVE, EnumSet.of(MemberStatus.INACTIVE),
            MemberStatus.INACTIVE, EnumSet.of(MemberStatus.ACTIVE)
    );

    private final MemberId id;
    private final AssociationId associationId;
    private final ContactDetails contact;
    private final GdprConsent consent;
    private final MemberStatus status;
    private final LevelId levelId;
    private final Set<MemberRole> roles;
    private final List<LevelChange> levelChanges;
    private final Instant joinedAt;
    private final Instant anonymisedAt;

    private Member(MemberId id, AssociationId associationId, ContactDetails contact, GdprConsent consent, MemberStatus status, LevelId levelId, Set<MemberRole> roles,
                   List<LevelChange> levelChanges, Instant joinedAt, Instant anonymisedAt) {
        this.id = id;
        this.associationId = associationId;
        this.contact = contact;
        this.consent = consent;
        this.status = status;
        this.levelId = levelId;
        this.roles = roles;
        this.levelChanges = levelChanges;
        this.joinedAt = joinedAt;
        this.anonymisedAt = anonymisedAt;
    }

    /**
     * A new, ACTIVE member at the association's entry level (RN-20). Used when a join request is
     * approved (roles {MEMBER}) and when someone registers an association (roles {ADMIN}).
     */
    public static Member create(Association association, ContactDetails contact, GdprConsent consent,
                                Set<MemberRole> roles, Clock clock) {
        Objects.requireNonNull(association, "association must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        return reconstruct(MemberId.generate(), association.id(), contact, consent, MemberStatus.ACTIVE,
                association.entryLevelId(), roles, List.of(), clock.instant(), null);
    }

    /** Rebuilds a member from persisted data, re-checking its invariants. */
    public static Member reconstruct(MemberId id, AssociationId associationId, ContactDetails contact,
                                     GdprConsent consent, MemberStatus status, LevelId levelId, Set<MemberRole> roles,
                                     List<LevelChange> levelChanges, Instant joinedAt, Instant anonymisedAt) {
        Objects.requireNonNull(contact, "contact must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(levelId, "levelId must not be null");
        Objects.requireNonNull(roles, "roles must not be null");
        Objects.requireNonNull(levelChanges, "levelChanges must not be null");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        if (roles.isEmpty()) {
            throw new InvalidMemberException("A member needs at least one role");
        }
        if (anonymisedAt != null && status != MemberStatus.INACTIVE) {
            throw new InvalidMemberException("An anonymised member must be inactive");
        }
        if (anonymisedAt == null && contact.phone().isEmpty()) {
            throw new InvalidMemberException("A member who is not anonymised needs a phone number");
        }
        if (anonymisedAt != null && anonymisedAt.isBefore(joinedAt)) {
            throw new InvalidMemberException("A member cannot be anonymised before the member joined");
        }
        requireConsistentHistory(levelChanges, levelId, joinedAt);
        return new Member(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(associationId, "associationId must not be null"),
                contact,
                Objects.requireNonNull(consent, "consent must not be null"),
                status, levelId, Set.copyOf(roles), List.copyOf(levelChanges), joinedAt, anonymisedAt
        );
    }

    /** The history must be a chain of moves that ends at the current level. */
    private static void requireConsistentHistory(List<LevelChange> changes, LevelId currentLevel, Instant joinedAt) {
        if (changes.stream().anyMatch(change -> change.changedAt().isBefore(joinedAt))) {
            throw new InvalidMemberException("A level change cannot be dated before the member joined");
        }
        for (int i = 1; i < changes.size(); i++) {
            LevelChange previous = changes.get(i - 1);
            LevelChange change = changes.get(i);
            if (!change.from().equals(previous.to())) {
                throw new InvalidMemberException("Level history is not a continuous chain of moves");
            }
            if (change.changedAt().isBefore(previous.changedAt())) {
                throw new InvalidMemberException("Level history is not in chronological order");
            }
        }
        if (!changes.isEmpty() && !changes.get(changes.size() - 1).to().equals(currentLevel)) {
            throw new InvalidMemberException("Level history must end at the member's current level");
        }
    }

    /**
     * Moves the member to the association's level {@code newLevelId} and records who did it and
     * when (US-07, RN-20). The new level applies to bookings made from now on, since eligibility
     * reads the current level. Moving to the level they already have changes nothing and records
     * nothing.
     *
     * @param changedBy the coach or administrator who made the change; the caller has authorised them
     * @throws IllegalArgumentException if {@code association} isn't the member's
     * @throws com.regivolley.api.domain.exception.LevelNotFoundException if the level isn't one of the association's
     * @throws MemberAnonymisedException if the member was anonymised
     */
    public Member changeLevel(Association association, LevelId newLevelId, MemberId changedBy, Clock clock) {
        Objects.requireNonNull(association, "association must not be null");
        Objects.requireNonNull(newLevelId, "newLevelId must not be null");
        Objects.requireNonNull(changedBy, "changedBy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        requireNotAnonymised();
        if (!association.id().equals(associationId)) {
            throw new IllegalArgumentException("The member belongs to another association");
        }
        Level newLevel = association.level(newLevelId);
        if (newLevel.id().equals(levelId)) {
            return this;
        }
        List<LevelChange> history = new ArrayList<>(levelChanges);
        history.add(new LevelChange(levelId, newLevel.id(), changedBy, clock.instant()));
        return copy(status, newLevel.id(), roles, history);
    }

    /** ACTIVE -> INACTIVE (US-08). Their future bookings are cancelled by the use case, not here. */
    public Member deactivate() {
        return transitionTo(MemberStatus.INACTIVE);
    }

    /**
     * INACTIVE -> ACTIVE.
     *
     * @throws MemberAnonymisedException if the member was anonymised
     */
    public Member reactivate() {
        requireNotAnonymised();
        return transitionTo(MemberStatus.ACTIVE);
    }

    private Member transitionTo(MemberStatus target) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(target)) {
            throw new InvalidMemberStatusTransitionException(status, target);
        }
        return copy(target, levelId, roles, levelChanges);
    }

    /** @throws MemberAnonymisedException if the member was anonymised */
    public Member grantRole(MemberRole role) {
        Objects.requireNonNull(role, "role must not be null");
        requireNotAnonymised();
        Set<MemberRole> updated = EnumSet.copyOf(roles);
        updated.add(role);
        return copy(status, levelId, updated, levelChanges);
    }

    /**
     * Revoking a role the member does not hold changes nothing.
     *
     * @throws LastRoleCannotBeRevokedException if it is the member's only role
     * @throws MemberAnonymisedException        if the member was anonymised
     */
    public Member revokeRole(MemberRole role) {
        Objects.requireNonNull(role, "role must not be null");
        requireNotAnonymised();
        Set<MemberRole> updated = EnumSet.copyOf(roles);
        updated.remove(role);
        if (updated.isEmpty()) {
            throw new LastRoleCannotBeRevokedException();
        }
        return copy(status, levelId, updated, levelChanges);
    }

    /**
     * RGPD erasure of this member: replaces name, email and phone with placeholders, deactivates
     * the member and reduces their roles to MEMBER, so an erased person keeps no privilege. Keeps
     * the id, level history, consent record and join date. Irreversible and idempotent: an
     * already anonymised member is returned as is. This does not erase the person's join
     * requests or other records; see the class comment.
     */
    public Member anonymise(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (isAnonymised()) {
            return this;
        }
        return reconstruct(id, associationId, ContactDetails.anonymisedFor(id.value()), consent,
                MemberStatus.INACTIVE, levelId, Set.of(MemberRole.MEMBER), levelChanges, joinedAt, clock.instant());
    }

    private void requireNotAnonymised() {
        if (isAnonymised()) {
            throw new MemberAnonymisedException();
        }
    }

    private Member copy(MemberStatus newStatus, LevelId newLevelId, Set<MemberRole> newRoles,
                        List<LevelChange> newHistory) {
        return reconstruct(id, associationId, contact, consent, newStatus, newLevelId, newRoles,
                newHistory, joinedAt, anonymisedAt);
    }

    /**
     * The facts booking eligibility needs about this member (RN-06, RN-14, RN-21): status, the
     * rank of the current level and all their subscriptions (which {@link MemberBookingProfile}
     * checks belong to this member and association).
     *
     * @throws IllegalArgumentException if {@code association} isn't the member's
     */
    public MemberBookingProfile bookingProfile(Association association, List<Subscription> subscriptions) {
        Objects.requireNonNull(association, "association must not be null");
        if (!association.id().equals(associationId)) {
            throw new IllegalArgumentException("The member belongs to another association");
        }
        return new MemberBookingProfile(associationId, id, status, association.rankOf(levelId), subscriptions);
    }

    public boolean hasRole(MemberRole role) {
        return roles.contains(role);
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public boolean isAnonymised() {
        return anonymisedAt != null;
    }

    public MemberId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public ContactDetails contact() {
        return contact;
    }

    public String name() {
        return contact.name();
    }

    public EmailAddress email() {
        return contact.email();
    }

    /** Empty only once the member is anonymised. */
    public Optional<PhoneNumber> phone() {
        return contact.phone();
    }

    public GdprConsent consent() {
        return consent;
    }

    public MemberStatus status() {
        return status;
    }

    public LevelId levelId() {
        return levelId;
    }

    public Set<MemberRole> roles() {
        return roles;
    }

    /** Oldest first; empty if the member never moved from the entry level. */
    public List<LevelChange> levelChanges() {
        return levelChanges;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Optional<Instant> anonymisedAt() {
        return Optional.ofNullable(anonymisedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Member{id=%s, associationId=%s, status=%s}".formatted(id, associationId, status);
    }
}
