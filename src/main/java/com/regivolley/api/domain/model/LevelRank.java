package com.regivolley.api.domain.model;

import java.util.Collection;
import java.util.Objects;

/**
 * A level's position in the association's ordered levels (US-03): a higher rank is a more
 * advanced level. This is the minimal level abstraction booking eligibility needs (RN-14,
 * RN-21); the full Level model (name, entry level, ordering management) is issue #17, whose
 * {@code Level} aggregate only has to expose its {@code LevelRank} for eligibility to plug in.
 */
public record LevelRank(LevelId levelId, int rank) {

    public LevelRank {
        Objects.requireNonNull(levelId, "levelId must not be null");
        if (rank < 0) {
            throw new IllegalArgumentException("rank must not be negative");
        }
    }

    /** Whether this level is the same as, or more advanced than, {@code other}. */
    public boolean isAtLeast(LevelRank other) {
        return rank >= other.rank;
    }

    /**
     * RN-21 default: a member may book in their own level and the lower ones, so a group
     * accepting several levels is open to anyone at or above its lowest accepted level.
     */
    public boolean canBookGroupAccepting(Collection<LevelRank> acceptedLevels) {
        return acceptedLevels.stream().anyMatch(this::isAtLeast);
    }
}
