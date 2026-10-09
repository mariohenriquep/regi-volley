package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** One booking of the session and whether the member came ({@code ATTENDED}) or not ({@code NO_SHOW}). */
public record AttendanceEntryRequest(@NotNull UUID bookingId, @NotNull AttendanceMarkName mark) {
}
