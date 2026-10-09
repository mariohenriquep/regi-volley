package com.regivolley.api.infrastructure.web.dto;

/** The session after its attendance was marked and how many members reached the no-show limit (RN-11) and were warned. */
public record AttendanceMarkedResponse(SessionResponse session, int warnedCount) {
}
