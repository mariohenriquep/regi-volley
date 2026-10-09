package com.regivolley.api.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

/** The deactivated member, how many of their bookings were cancelled and the sessions where that failed and needs a retry. */
public record MemberDeactivationResponse(MemberResponse member, int bookingsCancelled, List<UUID> failedSessionIds) {
}
