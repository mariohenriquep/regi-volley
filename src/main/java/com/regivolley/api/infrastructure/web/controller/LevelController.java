package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.AddLevelUseCase;
import com.regivolley.api.application.usecase.ChangeEntryLevelUseCase;
import com.regivolley.api.application.usecase.RenameLevelUseCase;
import com.regivolley.api.application.usecase.ReorderLevelsUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.LevelIdRequest;
import com.regivolley.api.infrastructure.web.dto.LevelNameRequest;
import com.regivolley.api.infrastructure.web.dto.LevelsResponse;
import com.regivolley.api.infrastructure.web.dto.ReorderLevelsRequest;
import com.regivolley.api.infrastructure.web.mapper.LevelWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The levels of the caller's association (US-03), for administrators (the use cases check). Every operation answers with the
 * association's levels after it, lowest first, and the entry level.
 */
@RestController
@RequestMapping("/api/v1/levels")
public class LevelController {

    private final AddLevelUseCase addLevel;
    private final RenameLevelUseCase renameLevel;
    private final ReorderLevelsUseCase reorderLevels;
    private final ChangeEntryLevelUseCase changeEntryLevel;

    public LevelController(AddLevelUseCase addLevel, RenameLevelUseCase renameLevel, ReorderLevelsUseCase reorderLevels,
                           ChangeEntryLevelUseCase changeEntryLevel) {
        this.addLevel = addLevel;
        this.renameLevel = renameLevel;
        this.reorderLevels = reorderLevels;
        this.changeEntryLevel = changeEntryLevel;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LevelsResponse add(@CurrentActor AuthenticatedActor caller, @Valid @RequestBody LevelNameRequest body) {
        return LevelWebMapper.toResponse(addLevel.execute(LevelWebMapper.addCommand(caller.actor(), body)));
    }

    @PutMapping("/{levelId}")
    public LevelsResponse rename(@CurrentActor AuthenticatedActor caller, @PathVariable UUID levelId,
                                 @Valid @RequestBody LevelNameRequest body) {
        return LevelWebMapper.toResponse(renameLevel.execute(LevelWebMapper.renameCommand(caller.actor(), levelId, body)));
    }

    @PutMapping("/order")
    public LevelsResponse reorder(@CurrentActor AuthenticatedActor caller, @Valid @RequestBody ReorderLevelsRequest body) {
        return LevelWebMapper.toResponse(reorderLevels.execute(LevelWebMapper.reorderCommand(caller.actor(), body)));
    }

    @PutMapping("/entry-level")
    public LevelsResponse entryLevel(@CurrentActor AuthenticatedActor caller, @Valid @RequestBody LevelIdRequest body) {
        return LevelWebMapper.toResponse(changeEntryLevel.execute(LevelWebMapper.entryLevelCommand(caller.actor(), body)));
    }
}
