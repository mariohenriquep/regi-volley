package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** A level of the caller's association: the new entry level (US-03) or a member's new level (US-04). */
public record LevelIdRequest(@NotNull UUID levelId) {
}
