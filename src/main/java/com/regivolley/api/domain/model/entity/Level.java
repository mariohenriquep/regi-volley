package com.regivolley.api.domain.model.entity;

import com.regivolley.api.domain.exception.InvalidAssociationException;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.LevelRank;
import com.regivolley.api.domain.shared.Entity;
import com.regivolley.api.domain.shared.FieldRules;

import java.util.Objects;

/**
 * A skill level of an association (US-03, RN-20, RN-21): a name and a rank, where a higher rank
 * is more advanced. Immutable. A level only lives inside its {@link Association}, which owns the
 * rules that span levels (at least one, exactly one entry level, ranks 0..n-1 without gaps,
 * unique names) and is the only place levels are created and changed.
 */
public final class Level implements Entity {

    public static final int MAX_NAME_LENGTH = 50;

    private final LevelId id;
    private final AssociationId associationId;
    private final String name;
    private final int rank;

    /**
     * Checks every invariant, so no level exists in an invalid state. Public so that {@code AssociationFactory} can
     * reconstitute one with its association; a level is created and changed only by its {@link Association}, and the
     * architecture test lets nothing else construct one.
     */
    public Level(LevelId id, AssociationId associationId, String name, int rank) {
        if (rank < 0) {
            throw new InvalidAssociationException("A level rank must not be negative");
        }
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.associationId = Objects.requireNonNull(associationId, "associationId must not be null");
        this.name = FieldRules.requiredText("level name", name, MAX_NAME_LENGTH);
        this.rank = rank;
    }

    Level renamedTo(String newName) {
        return new Level(id, associationId, newName, rank);
    }

    Level withRank(int newRank) {
        return new Level(id, associationId, name, newRank);
    }

    /** The minimal view booking eligibility needs (RN-14, RN-21). */
    public LevelRank toRank() {
        return new LevelRank(id, rank);
    }

    public LevelId id() {
        return id;
    }

    public AssociationId associationId() {
        return associationId;
    }

    public String name() {
        return name;
    }

    /** 0 is the most basic level; a higher rank is more advanced. */
    public int rank() {
        return rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Level other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Level{id=%s, rank=%d}".formatted(id, rank);
    }
}
