package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * A venue of an association (table {@code venues}); {@code version} is its optimistic lock.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code VenuePersistenceMapper}; never exposed outside {@code infrastructure.persistence}.
 */
@Entity
@Table(name = "venues")
public class VenueJpaEntity {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false, updatable = false)
    private UUID associationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "courts", nullable = false)
    private int courts;

    @Version
    @Column(nullable = false)
    private Long version;

    public VenueJpaEntity() {
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getCourts() {
        return courts;
    }

    public void setCourts(int courts) {
        this.courts = courts;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
