package com.regivolley.api.infrastructure.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One weekly slot in Lisbon local time: the day, the start as {@code HH:mm} and the length in minutes (RN-01). */
public record WeeklySlotRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull @Min(1) @Max(240) Integer durationMinutes) {
}
