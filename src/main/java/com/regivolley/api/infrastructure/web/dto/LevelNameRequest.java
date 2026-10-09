package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The name of a level to add or to rename a level to (US-03). */
public record LevelNameRequest(@NotBlank @Size(max = 50) String name) {
}
