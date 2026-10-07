package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A training session and its bookings (table {@code sessions}). {@code version} is the optimistic lock that protects the last seat (architecture.md section 10).
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code SessionPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "sessions")
public class SessionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "training_group_id", nullable = false)
    private UUID trainingGroupId;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("requestedAt ASC, id ASC")
    private List<BookingJpaEntity> bookings = new ArrayList<>();

    public SessionJpaEntity() {
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

    public UUID getTrainingGroupId() {
        return trainingGroupId;
    }

    public void setTrainingGroupId(UUID trainingGroupId) {
        this.trainingGroupId = trainingGroupId;
    }

    public UUID getCoachId() {
        return coachId;
    }

    public void setCoachId(UUID coachId) {
        this.coachId = coachId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<BookingJpaEntity> getBookings() {
        return bookings;
    }

    public void setBookings(List<BookingJpaEntity> bookings) {
        this.bookings = bookings;
    }
}
