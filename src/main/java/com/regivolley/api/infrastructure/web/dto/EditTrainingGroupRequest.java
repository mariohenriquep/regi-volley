package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Everything about a group an administrator may change (US-09); the venue is fixed once the group exists. */
public record EditTrainingGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @Size(min = 1, max = 20) Set<@NotNull UUID> acceptedLevelIds,
        @NotNull @Size(min = 1, max = 14) List<@NotNull @Valid WeeklySlotRequest> schedule,
        @NotNull @Min(1) @Max(1000) Integer capacity,
        @NotNull UUID coachId) {
}
