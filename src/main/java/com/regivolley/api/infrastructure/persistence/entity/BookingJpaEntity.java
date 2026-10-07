package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking inside a session (table {@code bookings}); saved only through its {@link SessionJpaEntity}.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code BookingPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "bookings")
public class BookingJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, updatable = false)
    private SessionJpaEntity session;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancellation_kind")
    private String cancellationKind;

    public BookingJpaEntity() {
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

    public SessionJpaEntity getSession() {
        return session;
    }

    public void setSession(SessionJpaEntity session) {
        this.session = session;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public void setMemberId(UUID memberId) {
        this.memberId = memberId;
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

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getCancellationKind() {
        return cancellationKind;
    }

    public void setCancellationKind(String cancellationKind) {
        this.cancellationKind = cancellationKind;
    }
}
