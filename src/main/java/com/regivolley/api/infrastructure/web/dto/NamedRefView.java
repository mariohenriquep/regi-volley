package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** An id with the name to show beside it (a level, a venue, a coach); {@code name} is left out when it cannot be found. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NamedRefView(UUID id, String name) {
}
