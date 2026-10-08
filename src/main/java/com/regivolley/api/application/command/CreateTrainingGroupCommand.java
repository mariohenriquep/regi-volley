package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;

import java.util.Objects;
import java.util.Set;

/** An administrator creates a training group with a recurring schedule (US-09, RN-21). The coach must be an active member holding COACH; the levels and the venue must belong to the association. */
public record CreateTrainingGroupCommand(Actor actor, String name, Set<LevelId> acceptedLevels, VenueId venueId, WeeklySchedule schedule, int capacity, MemberId coachId) {

    public CreateTrainingGroupCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(acceptedLevels, "acceptedLevels must not be null");
        Objects.requireNonNull(venueId, "venueId must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(coachId, "coachId must not be null");
    }
}
