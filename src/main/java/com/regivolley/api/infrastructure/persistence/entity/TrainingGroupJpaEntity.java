package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A recurring training group (table {@code training_groups}); {@code version} is its optimistic lock.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code TrainingGroupPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "training_groups")
public class TrainingGroupJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "default_capacity", nullable = false)
    private int defaultCapacity;

    @Column(name = "coach_id", nullable = false)
    private UUID coachId;

    @Column(name = "status", nullable = false)
    private String status;

    @Version
    @Column(nullable = false)
    private Long version;

    @ElementCollection
    @CollectionTable(name = "training_group_accepted_levels", joinColumns = @JoinColumn(name = "training_group_id"))
    private Set<LevelRef> acceptedLevels = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "training_group_slots", joinColumns = @JoinColumn(name = "training_group_id"))
    private Set<SlotRow> slots = new HashSet<>();

    public TrainingGroupJpaEntity() {
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

    public UUID getVenueId() {
        return venueId;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }

    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(int defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public UUID getCoachId() {
        return coachId;
    }

    public void setCoachId(UUID coachId) {
        this.coachId = coachId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Set<LevelRef> getAcceptedLevels() {
        return acceptedLevels;
    }

    public void setAcceptedLevels(Set<LevelRef> acceptedLevels) {
        this.acceptedLevels = acceptedLevels;
    }

    public Set<SlotRow> getSlots() {
        return slots;
    }

    public void setSlots(Set<SlotRow> slots) {
        this.slots = slots;
    }

    /** One weekly slot (table {@code training_group_slots}). */
    @Embeddable
    public static class SlotRow {

        @Column(name = "association_id", nullable = false)
        private UUID associationId;

        @Column(name = "day_of_week", nullable = false)
        private int dayOfWeek;

        @Column(name = "start_time", nullable = false)
        private LocalTime startTime;

        @Column(name = "duration_minutes", nullable = false)
        private int durationMinutes;

        public SlotRow() {
        }

        public SlotRow(UUID associationId, int dayOfWeek, LocalTime startTime, int durationMinutes) {
            this.associationId = associationId;
            this.dayOfWeek = dayOfWeek;
            this.startTime = startTime;
            this.durationMinutes = durationMinutes;
        }

        public UUID getAssociationId() {
            return associationId;
        }

        public int getDayOfWeek() {
            return dayOfWeek;
        }

        public LocalTime getStartTime() {
            return startTime;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SlotRow other)) return false;
            return Objects.equals(associationId, other.associationId) && Objects.equals(dayOfWeek, other.dayOfWeek) && Objects.equals(startTime, other.startTime) && Objects.equals(durationMinutes, other.durationMinutes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(associationId, dayOfWeek, startTime, durationMinutes);
        }
    }
}
