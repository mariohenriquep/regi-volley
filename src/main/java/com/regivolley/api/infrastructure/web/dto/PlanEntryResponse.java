package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/**
 * A plan in the administrator's list; the terms that do not go with its type are absent, the price is in cents and
 * {@code allowedLevels} (lowest first) is empty when the plan gives access to every level.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanEntryResponse(UUID id, String name, String type, Integer sessionsPerWeek, Integer credits, long priceCents,
                                Integer validityDays, List<NamedRefView> allowedLevels) {
}
