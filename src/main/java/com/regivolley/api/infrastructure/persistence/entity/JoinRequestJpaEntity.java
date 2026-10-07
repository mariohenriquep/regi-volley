package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A request to join an association (table {@code join_requests}); name, email and phone are personal data and are never logged.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code JoinRequestPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "join_requests")
public class JoinRequestJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    @Column(name = "consent_policy_version", nullable = false)
    private String consentPolicyVersion;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "anonymised_at")
    private Instant anonymisedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public JoinRequestJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public void setAssociationId(UUID associationId) {
        this.associationId = associationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Instant getConsentGivenAt() {
        return consentGivenAt;
    }

    public void setConsentGivenAt(Instant consentGivenAt) {
        this.consentGivenAt = consentGivenAt;
    }

    public String getConsentPolicyVersion() {
        return consentPolicyVersion;
    }

    public void setConsentPolicyVersion(String consentPolicyVersion) {
        this.consentPolicyVersion = consentPolicyVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getAnonymisedAt() {
        return anonymisedAt;
    }

    public void setAnonymisedAt(Instant anonymisedAt) {
        this.anonymisedAt = anonymisedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
