package com.regivolley.api.application.command;

import com.regivolley.api.domain.model.valueobject.MemberId;
import com.regivolley.api.domain.model.valueobject.PlanId;

import java.time.LocalDate;
import java.util.Objects;

/** An administrator gives a member a plan (US-20, RN-16). A null {@code startDate} means "as soon as possible": the day after the member's latest subscription ends (a renewal; at once for an exhausted pack), or today for a member with none. */
public record AssignPlanCommand(Actor actor, MemberId memberId, PlanId planId, LocalDate startDate) {

    public AssignPlanCommand {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(memberId, "memberId must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
    }
}
