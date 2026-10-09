package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.ArchiveTrainingGroupCommand;
import com.regivolley.api.application.command.CreateTrainingGroupCommand;
import com.regivolley.api.application.command.EditTrainingGroupCommand;
import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import com.regivolley.api.infrastructure.web.dto.CreateTrainingGroupRequest;
import com.regivolley.api.infrastructure.web.dto.EditTrainingGroupRequest;
import com.regivolley.api.infrastructure.web.dto.TrainingGroupResponse;
import com.regivolley.api.infrastructure.web.dto.WeeklySlotRequest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Training groups (US-09): the weekly schedule travels as day, {@code HH:mm} Lisbon time and minutes. */
public final class TrainingGroupWebMapper {

    private TrainingGroupWebMapper() {
    }

    public static CreateTrainingGroupCommand createCommand(Actor actor, CreateTrainingGroupRequest body) {
        return new CreateTrainingGroupCommand(actor, body.name(), levels(body.acceptedLevelIds()), VenueId.of(body.venueId()),
                schedule(body.schedule()), body.capacity(), MemberId.of(body.coachId()));
    }

    public static EditTrainingGroupCommand editCommand(Actor actor, UUID groupId, EditTrainingGroupRequest body) {
        return new EditTrainingGroupCommand(actor, TrainingGroupId.of(groupId), body.name(), levels(body.acceptedLevelIds()),
                schedule(body.schedule()), body.capacity(), MemberId.of(body.coachId()));
    }

    public static ArchiveTrainingGroupCommand archiveCommand(Actor actor, UUID groupId) {
        return new ArchiveTrainingGroupCommand(actor, TrainingGroupId.of(groupId));
    }

    public static TrainingGroupResponse toResponse(TrainingGroup group) {
        return new TrainingGroupResponse(group.id().value(), group.name(),
                group.acceptedLevels().stream().map(LevelId::value).sorted().toList(), group.venueId().value(),
                group.schedule().slots().stream().map(slot -> new TrainingGroupResponse.SlotView(slot.dayOfWeek().name(),
                        slot.startTime().toString(), (int) slot.duration().toMinutes())).toList(),
                group.defaultCapacity(), group.coachId().value(), group.status().name());
    }

    private static Set<LevelId> levels(Set<UUID> ids) {
        return ids.stream().map(LevelId::of).collect(Collectors.toSet());
    }

    private static WeeklySchedule schedule(List<WeeklySlotRequest> slots) {
        return new WeeklySchedule(slots.stream()
                .map(slot -> new WeeklySlot(slot.dayOfWeek(), slot.startTime(), Duration.ofMinutes(slot.durationMinutes()))).toList());
    }
}
