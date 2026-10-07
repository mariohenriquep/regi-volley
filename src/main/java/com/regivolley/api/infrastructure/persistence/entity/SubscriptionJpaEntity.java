package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A member's subscription to a plan, with the plan terms it snapshotted and the bookings holding a place (table {@code subscriptions}); {@code version} is its optimistic lock, so two bookings cannot spend the same last credit.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code SubscriptionPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "subscriptions")
public class SubscriptionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Column(name = "sessions_per_week")
    private Integer sessionsPerWeek;

    @Column(name = "credits")
    private Integer credits;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "payment_status", nullable = false)
    private String paymentStatus;

    @Version
    @Column(nullable = false)
    private Long version;

    @ElementCollection
    @CollectionTable(name = "subscription_allowed_levels", joinColumns = @JoinColumn(name = "subscription_id"))
    private Set<LevelRef> allowedLevels = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "subscription_credit_usages", joinColumns = @JoinColumn(name = "subscription_id"))
    @OrderColumn(name = "position")
    private List<UsageRow> usages = new ArrayList<>();

    public SubscriptionJpaEntity() {
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

    public UUID getMemberId() {
        return memberId;
    }

    public void setMemberId(UUID memberId) {
        this.memberId = memberId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public String getPlanType() {
        return planType;
    }

    public void setPlanType(String planType) {
        this.planType = planType;
    }

    public Integer getSessionsPerWeek() {
        return sessionsPerWeek;
    }

    public void setSessionsPerWeek(Integer sessionsPerWeek) {
        this.sessionsPerWeek = sessionsPerWeek;
    }

    public Integer getCredits() {
        return credits;
    }

    public void setCredits(Integer credits) {
        this.credits = credits;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Set<LevelRef> getAllowedLevels() {
        return allowedLevels;
    }

    public void setAllowedLevels(Set<LevelRef> allowedLevels) {
        this.allowedLevels = allowedLevels;
    }

    public List<UsageRow> getUsages() {
        return usages;
    }

    public void setUsages(List<UsageRow> usages) {
        this.usages = usages;
    }

    /** One booking holding a place (table {@code subscription_credit_usages}), kept in the order charged. */
    @Embeddable
    public static class UsageRow {

        @Column(name = "association_id", nullable = false)
        private UUID associationId;

        @Column(name = "booking_id", nullable = false)
        private UUID bookingId;

        @Column(name = "session_date", nullable = false)
        private LocalDate sessionDate;

        public UsageRow() {
        }

        public UsageRow(UUID associationId, UUID bookingId, LocalDate sessionDate) {
            this.associationId = associationId;
            this.bookingId = bookingId;
            this.sessionDate = sessionDate;
        }

        public UUID getAssociationId() {
            return associationId;
        }

        public UUID getBookingId() {
            return bookingId;
        }

        public LocalDate getSessionDate() {
            return sessionDate;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UsageRow other)) return false;
            return Objects.equals(associationId, other.associationId) && Objects.equals(bookingId, other.bookingId) && Objects.equals(sessionDate, other.sessionDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(associationId, bookingId, sessionDate);
        }
    }
}
