package com.regivolley.api.infrastructure.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/** A subscription as an administrator sees it: the terms it snapshotted are the plan's at the time; the price is in cents. */
public record SubscriptionResponse(UUID id, UUID memberId, UUID planId, String type, LocalDate startDate, LocalDate endDate,
                                   String paymentStatus, long priceCents, int creditsUsed) {
}
