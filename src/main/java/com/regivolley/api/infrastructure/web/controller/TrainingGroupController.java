package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.ArchiveTrainingGroupUseCase;
import com.regivolley.api.application.usecase.CreateTrainingGroupUseCase;
import com.regivolley.api.application.usecase.EditTrainingGroupUseCase;
import com.regivolley.api.application.usecase.ListTrainingGroupsUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.CreateTrainingGroupRequest;
import com.regivolley.api.infrastructure.web.dto.EditTrainingGroupRequest;
import com.regivolley.api.infrastructure.web.dto.TrainingGroupEntryResponse;
import com.regivolley.api.infrastructure.web.dto.TrainingGroupResponse;
import com.regivolley.api.infrastructure.web.mapper.TrainingGroupWebMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The training groups of the caller's association (US-09), for administrators (the use cases check). */
@RestController
@RequestMapping("/api/v1/training-groups")
public class TrainingGroupController {

    private final ListTrainingGroupsUseCase listGroups;
    private final CreateTrainingGroupUseCase createGroup;
    private final EditTrainingGroupUseCase editGroup;
    private final ArchiveTrainingGroupUseCase archiveGroup;

    public TrainingGroupController(ListTrainingGroupsUseCase listGroups, CreateTrainingGroupUseCase createGroup, EditTrainingGroupUseCase editGroup,
                                   ArchiveTrainingGroupUseCase archiveGroup) {
        this.listGroups = listGroups;
        this.createGroup = createGroup;
        this.editGroup = editGroup;
        this.archiveGroup = archiveGroup;
    }

    /** The active groups with their levels, venue and coach named; the archived ones too with {@code includeArchived=true}. */
    @GetMapping
    public List<TrainingGroupEntryResponse> list(@CurrentActor AuthenticatedActor caller,
                                                 @RequestParam(defaultValue = "false") boolean includeArchived) {
        return TrainingGroupWebMapper.toEntryResponses(listGroups.execute(TrainingGroupWebMapper.listQuery(caller.actor(), includeArchived)));
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
