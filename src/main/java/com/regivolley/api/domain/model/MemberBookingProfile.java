package com.regivolley.api.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * The facts about one member that booking eligibility needs, loaded by the use case: status and
 * level (from the Member aggregate, issue #17) and all the member's subscriptions. Every
 * subscription must belong to this member and to this association (architecture.md section 8).
 */
public record MemberBookingProfile(AssociationId associationId, MemberId memberId, MemberStatus status,
                                   LevelRank level, List<Subscription> subscriptions) {

    public MemberBookingProfile {
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(level, "level must not be null");
        Objects.requireNonNull(subscriptions, "subscriptions must not be null");
        subscriptions = List.copyOf(subscriptions);
        if (subscriptions.stream().anyMatch(subscription -> !subscription.memberId().equals(memberId))) {
            throw new IllegalArgumentException("A profile can only hold the member's own subscriptions");
        }
        if (subscriptions.stream().anyMatch(subscription -> !subscription.associationId().equals(associationId))) {
            throw new IllegalArgumentException("A profile can only hold subscriptions of its own association");
        }
    }
}
