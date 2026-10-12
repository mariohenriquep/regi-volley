package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The caller's own recent sessions, newest first, and this month's no-shows against the limit (US-18, RN-11). */
public record MemberHistoryResponse(List<HistoryEntryView> entries, int noShowsThisMonth, int noShowLimit, boolean nearLimit) {

    /** One past booking: the session, its group and venue by name (left out when they cannot be found), and the booking's final status. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryEntryView(UUID sessionId, UUID trainingGroupId, String trainingGroupName, String venueName,
                                   Instant startsAt, Instant endsAt, UUID bookingId, String status) {
    }
}
