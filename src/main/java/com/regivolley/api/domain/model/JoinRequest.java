package com.regivolley.api.domain.model;

import com.regivolley.api.domain.exception.InvalidJoinRequestException;
import com.regivolley.api.domain.exception.InvalidJoinRequestStatusTransitionException;

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
 * <p>{@link #toString()} prints ids only.
 */
public final class JoinRequest {

    public static final int MAX_REASON_LENGTH = 500;

    private static final Map<JoinRequestStatus, Set<JoinRequestStatus>> ALLOWED_TRANSITIONS = Map.of(
            JoinRequestStatus.PENDING, EnumSet.of(JoinRequestStatus.APPROVED, JoinRequestStatus.REJECTED),
            JoinRequestStatus.APPROVED, EnumSet.noneOf(JoinRequestStatus.class),
            JoinRequestStatus.REJECTED, EnumSet.noneOf(JoinRequestStatus.class)
    );

    private final JoinRequestId id;
    private final AssociationId associationId;
    private final String name;
    private final EmailAddress email;
    private final PhoneNumber phone;
    private final GdprConsent consent;
    private final JoinRequestStatus status;
    private final Instant requestedAt;
    private final Instant decidedAt;
    private final MemberId decidedBy;
    private final String rejectionReason;

    private JoinRequest(JoinRequestId id, AssociationId associationId, String name, EmailAddress email,
                        PhoneNumber phone, GdprConsent consent, JoinRequestStatus status, Instant requestedAt,
                        Instant decidedAt, MemberId decidedBy, String rejectionReason) {
        this.id = id;
        this.associationId = associationId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.consent = consent;
        this.status = status;
        this.requestedAt = requestedAt;
        this.decidedAt = decidedAt;
        this.decidedBy = decidedBy;
        this.rejectionReason = rejectionReason;
    }

    /**
     * A new PENDING request. The consent is stamped now, by the server clock.
     *
     * @param consentAccepted whether the person ticked the RGPD consent
     * @param policyVersion   the version of the privacy policy they were shown
     * @throws com.regivolley.api.domain.exception.ConsentRequiredException if the consent was not accepted
     */
    public static JoinRequest create(AssociationId associationId, String name, EmailAddress email, PhoneNumber phone,
                                     boolean consentAccepted, String policyVersion, Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        GdprConsent consent = GdprConsent.record(consentAccepted, policyVersion, clock);
        return reconstruct(JoinRequestId.generate(), associationId, name, email, phone, consent,
                JoinRequestStatus.PENDING, clock.instant(), null, null, null);
    }

    /** Rebuilds a request from persisted data, re-checking its invariants. */
    public static JoinRequest reconstruct(JoinRequestId id, AssociationId associationId, String name,
                                          EmailAddress email, PhoneNumber phone, GdprConsent consent,
                                          JoinRequestStatus status, Instant requestedAt, Instant decidedAt,
                                          MemberId decidedBy, String rejectionReason) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        boolean decided = status != JoinRequestStatus.PENDING;
        if (decided != (decidedAt != null) || decided != (decidedBy != null)) {
            throw new InvalidJoinRequestException("A decision time and decider are required exactly when the request is decided");
        }
        if (decidedAt != null && decidedAt.isBefore(requestedAt)) {
            throw new InvalidJoinRequestException("A request cannot be decided before it was made");
        }
        if (rejectionReason != null && status != JoinRequestStatus.REJECTED) {
            throw new InvalidJoinRequestException("Only a rejected request can have a rejection reason");
        }
        return new JoinRequest(
                Objects.requireNonNull(id, "id must not be null"),
                Objects.requireNonNull(associationId, "associationId must not be null"),
                FieldRules.requiredText("name", name, Member.MAX_NAME_LENGTH),
                Objects.requireNonNull(email, "email must not be null"),
                Objects.requireNonNull(phone, "phone must not be null"),
                Objects.requireNonNull(consent, "consent must not be null"),
                status, requestedAt, decidedAt, decidedBy, normaliseReason(rejectionReason)
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
        JoinRequest approved = decide(JoinRequestStatus.APPROVED, decidedBy, null, clock);
        Member member = Member.create(association, name, email, phone, consent, Set.of(MemberRole.MEMBER), clock);
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
        return reconstruct(id, associationId, name, email, phone, consent, target, requestedAt, clock.instant(),
                decider, reason);
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

    public String name() {
        return name;
    }

    public EmailAddress email() {
        return email;
    }

    public PhoneNumber phone() {
        return phone;
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

    /** The administrator who decided; present once the request is APPROVED or REJECTED. */
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
