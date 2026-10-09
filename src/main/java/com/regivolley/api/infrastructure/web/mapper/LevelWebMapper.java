package com.regivolley.api.infrastructure.web.mapper;

import com.regivolley.api.application.command.Actor;
import com.regivolley.api.application.command.AddLevelCommand;
import com.regivolley.api.application.command.ChangeEntryLevelCommand;
import com.regivolley.api.application.command.RenameLevelCommand;
import com.regivolley.api.application.command.ReorderLevelsCommand;
import com.regivolley.api.domain.model.entity.Association;
import com.regivolley.api.domain.model.entity.Level;
import com.regivolley.api.domain.model.valueobject.LevelId;
import com.regivolley.api.infrastructure.web.dto.LevelIdRequest;
import com.regivolley.api.infrastructure.web.dto.LevelNameRequest;
import com.regivolley.api.infrastructure.web.dto.LevelsResponse;
import com.regivolley.api.infrastructure.web.dto.ReorderLevelsRequest;

import java.util.Comparator;
import java.util.UUID;

/** Levels (US-03): requests to commands, and the association's levels back as the one answer every level operation gives. */
public final class LevelWebMapper {

    private LevelWebMapper() {
    }

    public static AddLevelCommand addCommand(Actor actor, LevelNameRequest body) {
        return new AddLevelCommand(actor, body.name());
    }

    public static RenameLevelCommand renameCommand(Actor actor, UUID levelId, LevelNameRequest body) {
        return new RenameLevelCommand(actor, LevelId.of(levelId), body.name());
    }

    public static ReorderLevelsCommand reorderCommand(Actor actor, ReorderLevelsRequest body) {
        return new ReorderLevelsCommand(actor, body.levelIds().stream().map(LevelId::of).toList());
    }

    public static ChangeEntryLevelCommand entryLevelCommand(Actor actor, LevelIdRequest body) {
        return new ChangeEntryLevelCommand(actor, LevelId.of(body.levelId()));
    }

    public static LevelsResponse toResponse(Association association) {
        return new LevelsResponse(association.levels().stream().sorted(Comparator.comparingInt(Level::rank))
                .map(level -> new LevelsResponse.LevelView(level.id().value(), level.name(), level.rank())).toList(),
                association.entryLevelId().value());
    }
}
