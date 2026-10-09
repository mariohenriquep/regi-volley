package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.CreatePlanUseCase;
import com.regivolley.api.application.usecase.EditPlanUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.PlanRequest;
import com.regivolley.api.infrastructure.web.dto.PlanResponse;
import com.regivolley.api.infrastructure.web.mapper.PlanWebMapper;
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

/** The plans of the caller's association (US-19), for administrators (the use cases check). Subscriptions keep the terms they were sold at. */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final CreatePlanUseCase createPlan;
    private final EditPlanUseCase editPlan;

    public PlanController(CreatePlanUseCase createPlan, EditPlanUseCase editPlan) {
        this.createPlan = createPlan;
        this.editPlan = editPlan;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponse create(@CurrentActor AuthenticatedActor caller, @Valid @RequestBody PlanRequest body) {
        return PlanWebMapper.toResponse(createPlan.execute(PlanWebMapper.createCommand(caller.actor(), body)));
    }

    @PutMapping("/{planId}")
    public PlanResponse edit(@CurrentActor AuthenticatedActor caller, @PathVariable UUID planId, @Valid @RequestBody PlanRequest body) {
        return PlanWebMapper.toResponse(editPlan.execute(PlanWebMapper.editCommand(caller.actor(), planId, body)));
    }
}
