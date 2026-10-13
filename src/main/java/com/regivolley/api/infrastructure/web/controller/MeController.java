package com.regivolley.api.infrastructure.web.controller;

import com.regivolley.api.application.usecase.GetMyRolesUseCase;
import com.regivolley.api.infrastructure.security.AuthenticatedActor;
import com.regivolley.api.infrastructure.security.CurrentActor;
import com.regivolley.api.infrastructure.web.dto.MeResponse;
import com.regivolley.api.infrastructure.web.mapper.MeWebMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who the caller is: the ids the security layer resolved for them and the roles their member holds now (read by a use case on every
 * call, so a revoked role is gone at once). It proves a token, the principal resolution and the tenant binding end to end, and tells
 * the PWA who it is acting as and what to show; the roles decide nothing here or in any other call.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final GetMyRolesUseCase getMyRoles;

    public MeController(GetMyRolesUseCase getMyRoles) {
        this.getMyRoles = getMyRoles;
    }

    @GetMapping
    public MeResponse me(@CurrentActor AuthenticatedActor caller) {
        return MeWebMapper.toResponse(caller.actor(), getMyRoles.execute(MeWebMapper.rolesQuery(caller.actor())));
    }
}
