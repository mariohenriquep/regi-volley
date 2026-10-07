package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.entity.JoinRequest;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.ContactDetails;
import com.regivolley.api.domain.model.valueobject.EmailAddress;
import com.regivolley.api.domain.model.valueobject.GdprConsent;
import com.regivolley.api.domain.model.valueobject.JoinRequestId;
import com.regivolley.api.domain.model.valueobject.JoinRequestStatus;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.PhoneNumber;
import com.regivolley.api.infrastructure.persistence.entity.JoinRequestJpaEntity;

/** Translates a {@link JoinRequest} to and from {@link JoinRequestJpaEntity}. */
public final class JoinRequestPersistenceMapper {

    private JoinRequestPersistenceMapper() {
    }

    /** Rebuilds the aggregate, re-checking its invariants. */
    public static JoinRequest toDomain(JoinRequestJpaEntity entity) {
        ContactDetails contact = ContactDetails.reconstruct(entity.getName(), new EmailAddress(entity.getEmail()),
                entity.getPhone() == null ? null : new PhoneNumber(entity.getPhone()));
        return JoinRequest.reconstruct(
                new JoinRequestId(entity.getId()),
                new AssociationId(entity.getAssociationId()),
                contact,
                new GdprConsent(entity.getConsentGivenAt(), entity.getConsentPolicyVersion()),
                JoinRequestStatus.valueOf(entity.getStatus()),
                entity.getRequestedAt(),
                entity.getDecidedAt(),
                entity.getDecidedBy() == null ? null : new MemberId(entity.getDecidedBy()),
                entity.getRejectionReason(),
                entity.getAnonymisedAt(),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /** Copies the request onto {@code entity} (new, or loaded and managed). The version stays with the persistence layer. */
    public static void apply(JoinRequest request, JoinRequestJpaEntity entity) {
        entity.setId(request.id().value());
        entity.setAssociationId(request.associationId().value());
        entity.setName(request.contact().name());
        entity.setEmail(request.contact().email().value());
        entity.setPhone(request.contact().phone().map(PhoneNumber::value).orElse(null));
        entity.setConsentGivenAt(request.consent().givenAt());
        entity.setConsentPolicyVersion(request.consent().policyVersion());
        entity.setStatus(request.status().name());
        entity.setRequestedAt(request.requestedAt());
        entity.setDecidedAt(request.decidedAt().orElse(null));
        entity.setDecidedBy(request.decidedBy().map(MemberId::value).orElse(null));
        entity.setRejectionReason(request.rejectionReason().orElse(null));
        entity.setAnonymisedAt(request.anonymisedAt().orElse(null));
    }
}
