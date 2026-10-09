package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The data of a venue, to create one or to replace what an existing one says (US-02). */
public record VenueRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200) String address,
        @NotNull @Min(1) @Max(1000) Integer courts) {
}
