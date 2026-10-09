package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The attendance of a session (US-17): all the entries are applied together or none is. */
public record MarkAttendanceRequest(@NotNull @Size(min = 1, max = 200) List<@NotNull @Valid AttendanceEntryRequest> entries) {
}
