package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * A plan, to create one or to replace what an existing one says (US-19, RN-13). Which of {@code sessionsPerWeek}, {@code credits}
 * and {@code validityDays} go with which type is the domain's rule and is answered with a 422 naming it. The price is in cents.
 */
public record PlanRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull PlanTypeName type,
        @Min(1) @Max(7) Integer sessionsPerWeek,
        @Min(1) @Max(1000) Integer credits,
        @Size(max = 20) Set<@NotNull UUID> allowedLevelIds,
        @NotNull @PositiveOrZero @Max(100_000_000) Long priceCents,
        @Min(1) @Max(3650) Integer validityDays) {
}
