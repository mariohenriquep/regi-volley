package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Every level of the association, lowest first (US-03). */
public record ReorderLevelsRequest(@NotNull @Size(min = 1, max = 20) List<@NotNull UUID> levelIds) {
}
