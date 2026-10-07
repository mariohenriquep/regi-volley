package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidJoinRequestException;
import com.regivolley.api.domain.exception.InvalidJoinRequestStatusTransitionException;
import com.regivolley.api.domain.model.result.JoinRequestApproval;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.MemberRole;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.domain.shared.AggregateRoot;
import com.regivolley.api.domain.shared.FieldRules;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A visitor's request to join an association (US-05, US-06). It collects only what the
 * requirements list (name, email, phone) plus the mandatory RGPD consent, and stays PENDING until
 * an administrator decides: PENDING -> APPROVED | REJECTED, both final. Immutable: a decision
 * returns a new instance.
 *
 * <p>Approving creates the {@link Member} at the association's entry level (RN-20), returned with
 * the approved request in a {@link JoinRequestApproval}; sending the email is the use case's job.
 * Rejecting may carry a reason.
 *
 * <p><b>Anonymisation (RGPD erasure)</b> replaces the contact details with placeholders and keeps
 * the id, status, decision audit (who and when) and consent record. A request still PENDING is
 * treated as withdrawn: it becomes REJECTED with the reason {@link #WITHDRAWN_REASON}, decided at
 * the erasure time by nobody (no decider), so a request that can never be answered does not stay
 * open. The free-text rejection reason of an already rejected request is dropped, since an
 * administrator may have typed personal data into it. Irreversible and idempotent.
 *
 * <p>{@link #toString()} prints ids only.
 */
public final class JoinRequest implements AggregateRoot {

    public static final int MAX_REASON_LENGTH = 500;
    public static final String WITHDRAWN_REASON = "Withdrawn on erasure request";

    private static final Map<JoinRequestStatus, Set<JoinRequestStatus>> ALLOWED_TRANSITIONS = Map.of(
            JoinRequestStatus.PENDING, EnumSet.of(JoinRequestStatus.APPROVED, JoinRequestStatus.REJECTED),
            JoinRequestStatus.APPROVED, EnumSet.noneOf(JoinRequestStatus.class),
            JoinRequestStatus.REJECTED, EnumSet.noneOf(JoinRequestStatus.class)
    );

    private final JoinRequestId id;
    private final AssociationId associationId;
    private final ContactDetails contact;
    private final GdprConsent consent;
    private final JoinRequestStatus status;
    private final Instant requestedAt;
    private final Instant decidedAt;
    private final MemberId decidedBy;
    private final String rejectionReason;
    private final Instant anonymisedAt;
    private final long version;

    private JoinRequest(JoinRequestId id, AssociationId associationId, ContactDetails contact, GdprConsent consent,
                        JoinRequestStatus status, Instant requestedAt, Instant decidedAt, MemberId decidedBy,
                        String rejectionReason, Instant anonymisedAt, long version) {
        this.id = id;
        this.associationId = associationId;
        this.contact = contact;
        this.consent = consent;
        this.status = status;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.rejectionReason = rejectionReason;
        this.anonymisedAt = anonymisedAt;
        this.version = version;
    }

    /**
     * A new PENDING request. The consent is stamped now, by the server clock.
     *
     * @param consentAccepted whether the person ticked the RGPD consent
     * @param policyVersion   the version of the privacy policy they were shown
     * @throws com.regivolley.api.domain.exception.ConsentRequiredException if the consent was not accepted
     */
    public static JoinRequest create(AssociationId associationId, ContactDetails contact, boolean consentAccepted,
                                     String policyVersion, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        Instant now = clock.instant();
        GdprConsent consent = GdprConsent.record(consentAccepted, policyVersion, now);
        return reconstruct(JoinRequestId.generate(), associationId, contact, consent,
                JoinRequestStatus.PENDING, now, null, null, null, null, 0L);
    }

    /**
     * Rebuilds a request from persisted data, re-checking its invariants. A decider is required
     * for every decision except the withdrawal of an erased request (REJECTED and anonymised).
     *
     * @param version the optimistic-lock version it was loaded with (0 for a new request); decisions and
     *                erasure carry it over, so approving and rejecting at once cannot both be stored
     *                (architecture.md section 10)
     */
    public static JoinRequest reconstruct(JoinRequestId id, AssociationId associationId, ContactDetails contact,
                                          GdprConsent consent, JoinRequestStatus status, Instant requestedAt,
                                          Instant decidedAt, MemberId decidedBy, String rejectionReason,
                                          Instant anonymisedAt, long version) {
        Objects.requireNonNull(contact, "contact must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        if (version < 0) {
            throw new InvalidJoinRequestException("The version must not be negative");
        }
        boolean decided = status != JoinRequestStatus.PENDING;
        boolean withdrawnOnErasure = status == JoinRequestStatus.REJECTED && anonymisedAt != null;
        if (decided != (decidedAt != null) || (!decided && decidedBy != null)
                || (decided && decidedBy == null && !withdrawnOnErasure)) {
            throw new InvalidJoinRequestException("A decision time and decider are required exactly when the request is decided");
        }
        if (decidedAt != null && decidedAt.isBefore(requestedAt)) {
            throw new InvalidJoinRequestException("A request cannot be decided before it was made");
        }
        if (rejectionReason != null && status != JoinRequestStatus.REJECTED) {
            throw new InvalidJoinRequestException("Only a rejected request can have a rejection reason");
        }
        if (anonymisedAt == null && contact.phone().isEmpty()) {
            throw new InvalidJoinRequestException("A request that is not anonymised needs a phone number");
        }
        if (anonymisedAt != null && status == JoinRequestStatus.PENDING) {
            throw new InvalidJoinRequestException("An anonymised request cannot be pending");
        }
        if (anonymisedAt != null && anonymisedAt.isBefore(requestedAt)) {
            throw new InvalidJoinRequestException("A request cannot be anonymised before it was made");
        }
        return new JoinRequest(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(associationId, "associationId must not be null"),
                contact,
                Objects.requireNonNull(consent, "consent must not be null"),
                status, requestedAt, decidedAt, decidedBy, normaliseReason(rejectionReason), anonymisedAt, version
        );
    }

    private static String normaliseReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        FieldRules.requireMaxLength("rejection reason", trimmed, MAX_REASON_LENGTH);
        return trimmed;
    }

    /**
     * PENDING -> APPROVED (US-06): the person becomes an ACTIVE member with the MEMBER role, at
     * the association's entry level (RN-20), carrying the consent they gave.
     *
     * @param decidedBy the administrator who approved; the caller has authorised them
     * @throws IllegalArgumentException if {@code association} isn't the request's
     */
    public JoinRequestApproval approve(Association association, MemberId decidedBy, Clock clock) {
        Objects.requireNonNull(association, "association must not be null");
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        if (!association.id().equals(associationId)) {
            throw new IllegalArgumentException("The join request belongs to another association");
        }
        Clock decisionTime = Clock.fixed(clock.instant(), clock.getZone());
        JoinRequest approved = decide(JoinRequestStatus.APPROVED, decidedBy, null, decisionTime);
        Member member = Member.create(association, contact, consent, Set.of(MemberRole.MEMBER), decisionTime);
        return new JoinRequestApproval(approved, member);
    }

    /**
     * PENDING -> REJECTED (US-06).
     *
     * @param reason optional; null or blank means none
     */
    public JoinRequest reject(MemberId decidedBy, String reason, Clock clock) {
        Objects.requireNonNull(decidedBy, "decidedBy must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        return decide(JoinRequestStatus.REJECTED, decidedBy, reason, clock);
    }

    private JoinRequest decide(JoinRequestStatus target, MemberId decider, String reason, Clock clock) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(target)) {
            throw new InvalidJoinRequestStatusTransitionException(status, target);
        }
        return reconstruct(id, associationId, contact, consent, target, requestedAt, clock.instant(),
                decider, reason, anonymisedAt, version);
    }

    /**
     * RGPD erasure of this request: replaces the contact details with placeholders, keeping the
     * id, status, decision audit and consent record. A PENDING request becomes REJECTED with
     * {@link #WITHDRAWN_REASON} and no decider; an already rejected one loses its free-text
     * reason. Idempotent: an already anonymised request is returned as is.
     */
    public JoinRequest anonymise(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        if (isAnonymised()) {
            return this;
        }
        Instant now = clock.instant();
        ContactDetails erased = ContactDetails.anonymisedFor(id.value());
        if (isPending()) {
            return reconstruct(id, associationId, erased, consent, JoinRequestStatus.REJECTED, requestedAt, now,
                    null, WITHDRAWN_REASON, now, version);
        }
        return reconstruct(id, associationId, erased, consent, status, requestedAt, decidedAt, decidedBy,
                null, now, version);
    }

    /** Optimistic-lock version: two decisions made at once on the same request cannot both be stored. */
    public long version() {
        return version;
    }

    public boolean isAnonymised() {
        return anonymisedAt != null;
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    public JoinRequestId id() {
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

    /** Empty only once the request is anonymised. */
    public Optional<PhoneNumber> phone() {
        return contact.phone();
    }

    public GdprConsent consent() {
        return consent;
    }

    public JoinRequestStatus status() {
        return status;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    /** Present once the request is APPROVED or REJECTED. */
    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    /** When the request was anonymised, if it was. */
    public Optional<Instant> anonymisedAt() {
        return Optional.ofNullable(anonymisedAt);
    }

    /** The administrator who decided; present once the request is APPROVED or REJECTED, except when withdrawn on erasure. */
    public Optional<MemberId> decidedBy() {
        return Optional.ofNullable(decidedBy);
    }

    /** Present only on a REJECTED request that was given a reason. */
    public Optional<String> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JoinRequest other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "JoinRequest{id=%s, associationId=%s, status=%s}".formatted(id, associationId, status);
    }
}
