package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The new number of seats of one session (US-11). */
public record ChangeCapacityRequest(@NotNull @Min(1) @Max(1000) Integer capacity) {
}
