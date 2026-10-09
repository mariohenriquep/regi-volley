package com.regivolley.api.infrastructure.web.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One line of "who owes what" (US-22). Personal data (the member's name): {@link #toString()} prints nothing. */
public record SubscriptionPaymentEntryResponse(UUID subscriptionId, UUID memberId, String memberName, UUID planId, String type,
                                               LocalDate startDate, LocalDate endDate, String paymentStatus, long priceCents) {

    @Override
    public String toString() {
        return "SubscriptionPaymentEntryResponse[redacted]";
    }
}
