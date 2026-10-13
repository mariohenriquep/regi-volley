package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.Plan;

import java.util.List;
import java.util.Objects;

/** A plan with the levels it gives access to, lowest first (none listed: no restriction, RN-14). */
public record PlanListEntry(Plan plan, List<Level> allowedLevels) {

    public PlanListEntry {
        Objects.requireNonNull(plan, "plan must not be null");
        allowedLevels = List.copyOf(Objects.requireNonNull(allowedLevels, "allowedLevels must not be null"));
    }
}
