package com.regivolley.api.application.result;

import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.entity.TrainingGroup;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A training group with the names an administrator reads beside its ids: the accepted levels (lowest first), the venue and the
 * coach (each name empty if it cannot be found).
 */
public record TrainingGroupListEntry(TrainingGroup group, List<Level> acceptedLevels, Optional<String> venueName, Optional<String> coachName) {

    public TrainingGroupListEntry {
        Objects.requireNonNull(group, "group must not be null");
        acceptedLevels = List.copyOf(Objects.requireNonNull(acceptedLevels, "acceptedLevels must not be null"));
        Objects.requireNonNull(venueName, "venueName must not be null");
        Objects.requireNonNull(coachName, "coachName must not be null");
    }
}
