package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * A training group in the administrator's list, with the names read beside its ids: the accepted levels (lowest first), the venue and
 * the coach (each name left out when it cannot be found). {@code status} is {@code ACTIVE} or {@code ARCHIVED}; times are Lisbon local time.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrainingGroupEntryResponse(UUID id, String name, List<NamedRefView> acceptedLevels, NamedRefView venue,
                                         List<GroupEntrySlotView> schedule, int capacity, NamedRefView coach, String status) {

    public record GroupEntrySlotView(String dayOfWeek, String startTime, int durationMinutes) {
    }
}
