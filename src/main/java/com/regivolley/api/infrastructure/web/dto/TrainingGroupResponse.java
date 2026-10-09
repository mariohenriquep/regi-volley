package com.regivolley.api.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

/** A training group; {@code status} is {@code ACTIVE} or {@code ARCHIVED}. Times are Lisbon local time. */
public record TrainingGroupResponse(UUID id, String name, List<UUID> acceptedLevelIds, UUID venueId, List<SlotView> schedule, int capacity,
                                    UUID coachId, String status) {

    public record SlotView(String dayOfWeek, String startTime, int durationMinutes) {
    }
}
