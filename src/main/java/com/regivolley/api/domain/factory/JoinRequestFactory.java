package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Creates and reconstitutes {@link JoinRequest}s: the only place a request is born. {@link JoinRequest}'s constructor
 * checks every invariant, so neither path can produce an invalid request. Stateless, so its methods are static.
 */
public final class JoinRequestFactory {

    private JoinRequestFactory() {
    }

    /**
     * A new PENDING request (US-05) with a generated id, at version 0. The consent is stamped now, by the server clock.
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
        return new JoinRequest(JoinRequestId.generate(), associationId, contact, consent,
                JoinRequestStatus.PENDING, now, null, null, null, null, 0L);
    }

    /**
     * Rebuilds a request from persisted data. A decider is required for every decision except the withdrawal of an
     * erased request (REJECTED and anonymised).
     *
     * @param version the optimistic-lock version it was loaded with (architecture.md section 10)
     */
    public static JoinRequest reconstitute(JoinRequestId id, AssociationId associationId, ContactDetails contact,
                                           GdprConsent consent, JoinRequestStatus status, Instant requestedAt,
                                           Instant decidedAt, MemberId decidedBy, String rejectionReason,
                                           Instant anonymisedAt, long version) {
        return new JoinRequest(id, associationId, contact, consent, status, requestedAt, decidedAt, decidedBy,
                rejectionReason, anonymisedAt, version);
    }
}
