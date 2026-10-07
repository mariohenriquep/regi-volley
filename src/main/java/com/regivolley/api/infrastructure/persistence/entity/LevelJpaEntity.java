package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A level of an association (table {@code levels}); saved only through its {@link AssociationJpaEntity}.
 *
 * <p>Persistence model only: translated to and from the domain aggregate by its
 * {@code LevelPersistenceMapper}; never exposed outside
 * {@code infrastructure.persistence}. Status-like columns hold the enum name as text.
 */
@Entity
@Table(name = "levels")
public class LevelJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false, updatable = false)
    private AssociationJpaEntity association;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "level_rank", nullable = false)
    private int levelRank;

    public LevelJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AssociationJpaEntity getAssociation() {
        return association;
    }

    public void setAssociation(AssociationJpaEntity association) {
        this.association = association;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getLevelRank() {
        return levelRank;
    }

    public void setLevelRank(int levelRank) {
        this.levelRank = levelRank;
    }
}
