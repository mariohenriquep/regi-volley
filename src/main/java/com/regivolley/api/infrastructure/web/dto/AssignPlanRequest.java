package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/** Gives the member in the path a plan (US-20). Without {@code startDate} the subscription starts as soon as possible. */
public record AssignPlanRequest(@NotNull UUID planId, @PlausibleDate LocalDate startDate) {
}
