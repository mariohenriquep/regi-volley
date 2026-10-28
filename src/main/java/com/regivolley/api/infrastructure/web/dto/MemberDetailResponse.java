package com.regivolley.api.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One member as an administrator sees them. An {@code anonymised} member (RGPD erasure) shows the placeholder name and no email or
 * phone. {@code subscriptions} are the most recent ones, newest first ({@code subscriptionsTruncated} when there were more); money is
 * in cents; {@code creditsLeft} is present for a pack or single session only. Personal data: {@link #toString()} prints nothing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberDetailResponse(UUID memberId, boolean anonymised, String name, String email, String phone, UUID levelId,
                                   String levelName, List<MemberRoleName> roles, String status, boolean accountActivated,
                                   Instant joinedAt, List<LevelChangeView> levelChanges, List<MemberSubscriptionView> subscriptions,
                                   boolean subscriptionsTruncated) {

    /** One move in the level history, oldest first; each name is left out when it cannot be found. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LevelChangeView(UUID fromLevelId, String fromLevelName, UUID toLevelId, String toLevelName, UUID changedBy,
                                  String changedByName, Instant changedAt) {
    }

    /** One of the member's subscriptions; {@code planName} is left out when the plan cannot be found. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MemberSubscriptionView(UUID subscriptionId, UUID planId, String planName, String type, LocalDate startDate,
                                         LocalDate endDate, String paymentStatus, long priceCents, long paidCents,
                                         long outstandingCents, int creditsUsed, Integer creditsLeft) {
    }

    @Override
    public String toString() {
        return "MemberDetailResponse[redacted]";
    }
}
