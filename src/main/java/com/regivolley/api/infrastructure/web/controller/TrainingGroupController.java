package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.ArchiveTrainingGroupUseCase;
import com.regivolley.api.application.usecase.CreateTrainingGroupUseCase;
import com.regivolley.api.application.usecase.EditTrainingGroupUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.CreateTrainingGroupRequest;
import com.regivolley.api.infrastructure.web.dto.EditTrainingGroupRequest;
import com.regivolley.api.infrastructure.web.dto.TrainingGroupResponse;
import com.regivolley.api.infrastructure.web.mapper.TrainingGroupWebMapper;
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

/** The training groups of the caller's association (US-09), for administrators (the use cases check). */
@RestController
@RequestMapping("/api/v1/training-groups")
public class TrainingGroupController {

    private final CreateTrainingGroupUseCase createGroup;
    private final EditTrainingGroupUseCase editGroup;
    private final ArchiveTrainingGroupUseCase archiveGroup;

    public TrainingGroupController(CreateTrainingGroupUseCase createGroup, EditTrainingGroupUseCase editGroup,
                                   ArchiveTrainingGroupUseCase archiveGroup) {
        this.createGroup = createGroup;
        this.editGroup = editGroup;
        this.archiveGroup = archiveGroup;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingGroupResponse create(@CurrentActor AuthenticatedActor caller, @Valid @RequestBody CreateTrainingGroupRequest body) {
        return TrainingGroupWebMapper.toResponse(createGroup.execute(TrainingGroupWebMapper.createCommand(caller.actor(), body)));
    }

    @PutMapping("/{groupId}")
    public TrainingGroupResponse edit(@CurrentActor AuthenticatedActor caller, @PathVariable UUID groupId,
                                      @Valid @RequestBody EditTrainingGroupRequest body) {
        return TrainingGroupWebMapper.toResponse(editGroup.execute(TrainingGroupWebMapper.editCommand(caller.actor(), groupId, body)));
    }

    @PostMapping("/{groupId}/archival")
    public TrainingGroupResponse archive(@CurrentActor AuthenticatedActor caller, @PathVariable UUID groupId) {
        return TrainingGroupWebMapper.toResponse(archiveGroup.execute(TrainingGroupWebMapper.archiveCommand(caller.actor(), groupId)));
    }
}
