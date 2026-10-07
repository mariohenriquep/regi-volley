package com.regivolley.api.domain.service;

import com.regivolley.api.domain.model.entity.Session;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.LevelRank;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a member wants to book, as far as eligibility is concerned: in which association, when
 * the session starts and which levels its group accepts (RN-21). The group's accepted levels
 * come from the training group (issue #17), so the caller supplies them. The association is the
 * tenant the whole decision runs in (architecture.md section 8).
 */
public record BookingTarget(AssociationId associationId, Instant sessionStart, Set<LevelRank> acceptedLevels) {

    public BookingTarget {
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(sessionStart, "sessionStart must not be null");
        Objects.requireNonNull(acceptedLevels, "acceptedLevels must not be null");
        if (acceptedLevels.isEmpty()) {
            throw new IllegalArgumentException("A group accepts at least one level");
        }
        acceptedLevels = Set.copyOf(acceptedLevels);
    }

    public static BookingTarget of(Session session, Set<LevelRank> acceptedLevels) {
        return new BookingTarget(session.associationId(), session.startsAt(), acceptedLevels);
    }

    /** The accepted levels' ids, which is what a plan's allowed levels are expressed in (RN-14). */
    public Set<LevelId> acceptedLevelIds() {
        return acceptedLevels.stream().map(LevelRank::levelId).collect(Collectors.toUnmodifiableSet());
    }
}
