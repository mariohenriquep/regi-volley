package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An association, the tenant (table {@code associations}); {@code version} is its optimistic lock, also moved when its levels change.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code AssociationPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "associations")
public class AssociationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Column(name = "nif")
    private String nif;

    @Column(name = "locality", nullable = false)
    private String locality;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "booking_window_days", nullable = false)
    private int bookingWindowDays;

    @Column(name = "free_cancellation_hours", nullable = false)
    private int freeCancellationHours;

    @Column(name = "session_generation_weeks", nullable = false)
    private int sessionGenerationWeeks;

    @Column(name = "entry_level_id", nullable = false)
    private UUID entryLevelId;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "association", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("levelRank ASC")
    private List<LevelJpaEntity> levels = new ArrayList<>();

    public AssociationJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getNif() {
        return nif;
    }

    public void setNif(String nif) {
        this.nif = nif;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public int getBookingWindowDays() {
        return bookingWindowDays;
    }

    public void setBookingWindowDays(int bookingWindowDays) {
        this.bookingWindowDays = bookingWindowDays;
    }

    public int getFreeCancellationHours() {
        return freeCancellationHours;
    }

    public void setFreeCancellationHours(int freeCancellationHours) {
        this.freeCancellationHours = freeCancellationHours;
    }

    public int getSessionGenerationWeeks() {
        return sessionGenerationWeeks;
    }

    public void setSessionGenerationWeeks(int sessionGenerationWeeks) {
        this.sessionGenerationWeeks = sessionGenerationWeeks;
    }

    public UUID getEntryLevelId() {
        return entryLevelId;
    }

    public void setEntryLevelId(UUID entryLevelId) {
        this.entryLevelId = entryLevelId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<LevelJpaEntity> getLevels() {
        return levels;
    }

    public void setLevels(List<LevelJpaEntity> levels) {
        this.levels = levels;
    }
}
