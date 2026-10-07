package com.regivolley.api.infrastructure.persistence.mapper;

import com.regivolley.api.domain.model.entity.TrainingGroup;
import com.regivolley.api.domain.model.valueobject.AssociationId;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupId;
import com.regivolley.api.domain.model.valueobject.TrainingGroupStatus;
import com.regivolley.api.domain.model.valueobject.VenueId;
import com.regivolley.api.domain.model.valueobject.WeeklySchedule;
import com.regivolley.api.domain.model.valueobject.WeeklySlot;
import com.regivolley.api.infrastructure.persistence.entity.LevelRef;
import com.regivolley.api.infrastructure.persistence.entity.TrainingGroupJpaEntity;
import com.regivolley.api.infrastructure.persistence.entity.TrainingGroupJpaEntity.SlotRow;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Translates a {@link TrainingGroup} to and from {@link TrainingGroupJpaEntity}. */
public final class TrainingGroupPersistenceMapper {

    private TrainingGroupPersistenceMapper() {
    }

    /** Rebuilds the aggregate, re-checking its invariants. */
    public static TrainingGroup toDomain(TrainingGroupJpaEntity entity) {
        Set<LevelId> acceptedLevels = entity.getAcceptedLevels().stream()
                .map(ref -> new LevelId(ref.getLevelId()))
                .collect(Collectors.toSet());
        List<WeeklySlot> slots = entity.getSlots().stream()
                .map(row -> new WeeklySlot(DayOfWeek.of(row.getDayOfWeek()), row.getStartTime(),
                        Duration.ofMinutes(row.getDurationMinutes())))
                .toList();
        return TrainingGroup.reconstruct(
                new TrainingGroupId(entity.getId()),
                new AssociationId(entity.getAssociationId()),
                entity.getName(),
                acceptedLevels,
                new VenueId(entity.getVenueId()),
                new WeeklySchedule(slots),
                entity.getDefaultCapacity(),
                new MemberId(entity.getCoachId()),
                TrainingGroupStatus.valueOf(entity.getStatus()),
                entity.getVersion() == null ? 0L : entity.getVersion());
    }

    /** Copies the group onto {@code entity} (new, or loaded and managed). The version stays with the persistence layer. */
    public static void apply(TrainingGroup group, TrainingGroupJpaEntity entity) {
        UUID associationId = group.associationId().value();
        entity.setId(group.id().value());
        entity.setAssociationId(associationId);
        entity.setName(group.name());
        entity.setVenueId(group.venueId().value());
        entity.setDefaultCapacity(group.defaultCapacity());
        entity.setCoachId(group.coachId().value());
        entity.setStatus(group.status().name());
        CollectionSync.replace(entity.getAcceptedLevels(), group.acceptedLevels().stream()
                .map(level -> new LevelRef(associationId, level.value()))
                .collect(Collectors.toSet()));
        CollectionSync.replace(entity.getSlots(), group.schedule().slots().stream()
                .map(slot -> new SlotRow(associationId, slot.dayOfWeek().getValue(), slot.startTime(),
                        (int) slot.duration().toMinutes()))
                .collect(Collectors.toSet()));
    }
}
