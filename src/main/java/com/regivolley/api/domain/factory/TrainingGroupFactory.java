package com.regivolley.api.domain.factory;

import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;

import java.util.Set;

/**
 * Creates and reconstitutes {@link TrainingGroup}s: the only place a group is born. {@link TrainingGroup}'s
 * constructor checks every invariant, so neither path can produce an invalid group. Stateless, so its methods are static.
 */
public final class TrainingGroupFactory {

    private TrainingGroupFactory() {
    }

    /** A new ACTIVE group (US-09) with a generated id, at version 0. */
    public static TrainingGroup create(AssociationId associationId, String name, Set<LevelId> acceptedLevels,
                                       VenueId venueId, WeeklySchedule schedule, int defaultCapacity,
                                       MemberId coachId) {
        return new TrainingGroup(TrainingGroupId.generate(), associationId, name, acceptedLevels, venueId, schedule,
                defaultCapacity, coachId, TrainingGroupStatus.ACTIVE, 0L);
    }

    /**
     * Rebuilds a group from persisted data.
     *
     * @param version the optimistic-lock version it was loaded with (architecture.md section 10)
     */
    public static TrainingGroup reconstitute(TrainingGroupId id, AssociationId associationId, String name,
                                             Set<LevelId> acceptedLevels, VenueId venueId, WeeklySchedule schedule,
                                             int defaultCapacity, MemberId coachId, TrainingGroupStatus status,
                                             long version) {
        return new TrainingGroup(id, associationId, name, acceptedLevels, venueId, schedule, defaultCapacity,
                coachId, status, version);
    }
}
