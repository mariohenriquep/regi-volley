package com.regivolley.api.infrastructure.web.dto;

import java.util.List;
import java.util.UUID;

/** The levels of the association, lowest first, and which one new members start at (RN-20). */
public record LevelsResponse(List<LevelView> levels, UUID entryLevelId) {

    public record LevelView(UUID id, String name, int rank) {
    }
}
