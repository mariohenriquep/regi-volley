package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;

import java.util.Objects;
import java.util.Set;

/** An administrator edits a training group (US-09): everything but its venue is replaced by what is given here. Sessions already generated keep what they were created with. */
public record EditTrainingGroupCommand(Actor actor, TrainingGroupId groupId, String name, Set<LevelId> acceptedLevels, WeeklySchedule schedule, int capacity, MemberId coachId) {

    public EditTrainingGroupCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(groupId, "groupId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(acceptedLevels, "acceptedLevels must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(coachId, "coachId must not be null");
    }
}
