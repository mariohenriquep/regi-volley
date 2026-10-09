package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/** A plan; the fields that do not go with its type are absent. The price is in cents. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanResponse(UUID id, String name, String type, Integer sessionsPerWeek, Integer credits, List<UUID> allowedLevelIds,
                           long priceCents, Integer validityDays) {
}
