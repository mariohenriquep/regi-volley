package com.regivolley.api.infrastructure.web.dto;

import java.util.UUID;

/** A cancelled booking: whether it was late (RN-10), whether the credit came back, and how many people moved up from the waitlist. */
public record BookingCancelledResponse(UUID sessionId, UUID bookingId, boolean late, boolean creditRefunded, int promotedCount) {
}
