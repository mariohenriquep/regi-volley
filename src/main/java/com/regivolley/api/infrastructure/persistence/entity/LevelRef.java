package com.regivolley.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.UUID;

/**
 * One row of a "set of levels" child table (plan allowed levels, subscription allowed levels,
 * training group accepted levels): the level id plus the owning association, so the child table
 * carries its own {@code association_id} (architecture.md section 8).
 */
@Embeddable
public class LevelRef {

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(name = "level_id", nullable = false)
    private UUID levelId;

    public LevelRef() {
    }

    public LevelRef(UUID associationId, UUID levelId) {
        this.associationId = associationId;
        this.levelId = levelId;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public UUID getLevelId() {
        return levelId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LevelRef other)) return false;
        return Objects.equals(associationId, other.associationId) && Objects.equals(levelId, other.levelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(associationId, levelId);
    }
}
