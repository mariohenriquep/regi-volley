package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Member;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One member as the administrator sees them (US-04, US-07, US-20): the member (an anonymised one is shown with its placeholders), the
 * name of their level, whether they have activated their account, the level history oldest first and the most recent subscriptions,
 * newest first ({@code subscriptionsTruncated} when there were more than the detail shows).
 */
public record MemberDetail(Member member, Optional<String> levelName, boolean accountActivated, List<LevelChangeEntry> levelChanges,
                           List<MemberSubscriptionEntry> subscriptions, boolean subscriptionsTruncated) {

    public MemberDetail {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(levelName, "levelName must not be null");
        levelChanges = List.copyOf(Objects.requireNonNull(levelChanges, "levelChanges must not be null"));
        subscriptions = List.copyOf(Objects.requireNonNull(subscriptions, "subscriptions must not be null"));
    }
}
