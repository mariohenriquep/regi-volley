package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.valueobject.TrainingGroupId;

import java.util.List;

/**
 * Outcome of generating one association's sessions (US-10): how many were created and the groups that
 * were left out because their configuration is not usable (the coach is missing or lacks the COACH
 * role, or an accepted level is not one of the association's), for an administrator to fix.
 */
public record SessionGenerationReport(int sessionsCreated, List<TrainingGroupId> skippedGroups) {

    public SessionGenerationReport {
        skippedGroups = List.copyOf(skippedGroups);
    }
}
