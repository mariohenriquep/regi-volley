package com.regivolley.api.infrastructure.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The caller's own recent sessions, newest first, and this month's no-shows against the limit (US-18, RN-11). */
public record MemberHistoryResponse(List<EntryView> entries, int noShowsThisMonth, int noShowLimit, boolean nearLimit) {

    public record EntryView(UUID sessionId, UUID trainingGroupId, Instant startsAt, Instant endsAt, UUID bookingId, String status) {
    }
}
