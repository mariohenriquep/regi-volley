package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A plan an association sells (table {@code plans}); money is whole cents.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code PlanPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "plans")
public class PlanJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "plan_type", nullable = false)
    private String planType;

    @Column(name = "sessions_per_week")
    private Integer sessionsPerWeek;

    @Column(name = "credits")
    private Integer credits;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "validity_days")
    private Integer validityDays;

    @Version
    @Column(nullable = false)
    private Long version;

    @ElementCollection
    @CollectionTable(name = "plan_allowed_levels", joinColumns = @JoinColumn(name = "plan_id"))
    private Set<LevelRef> allowedLevels = new HashSet<>();

    public PlanJpaEntity() {
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

    public long getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(long priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getValidityDays() {
        return validityDays;
    }

    public void setValidityDays(Integer validityDays) {
        this.validityDays = validityDays;
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
}
